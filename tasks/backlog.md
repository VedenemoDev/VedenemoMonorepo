# Backlog

## Create ModelRoot Entity And Model Registry

Status: executed. Full task text retained here for history.

### Goal

Introduce the first concrete model root entity and expose a minimal API for
adding and listing currently known models.

This task intentionally moves beyond the original ping-only web API. Keep the
change narrow: no durable persistence, no parser, no model internals beyond the
root metadata, and no UX changes unless explicitly added later.

### Domain Model

Add a `ModelRoot` entity with these fields:

- `azName`: ASCII model identifier. Must be unique within the currently loaded
  model registry using case-insensitive comparison. Preserve the original case
  as entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the model. UTF-8 string. Must be non-blank.
  Uniqueness is not enforced.
- `version`: semantic version in `major.minor.patch` format. `major`, `minor`,
  and `patch` are non-negative integers. Leading zeroes are normalized away, so
  `01.002.3` becomes `1.2.3`.

Implementation constraints:

- Put Vedenemo-owned model types in a pure JDK module, most likely
  `vedenemo-model-api`, unless implementation analysis finds a better existing
  boundary.
- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak Javalin, JSON-library, or HTTP types into model/core/SPI modules.

### Model Registry

Add a process-local registry of currently loaded / known models.

Preferred interpretation of "application global":

- a single registry instance wired by the application composition root for the
  running process
- explicit constructor wiring
- no dependency injection framework
- avoid hidden static mutable global state unless explicitly approved

The registry must enforce `azName` uniqueness.

Uniqueness is case-insensitive, so `Example` and `example` conflict, but the
original submitted case is preserved for display and API responses.

### HTTP API

Add two endpoints in `vedenemo-web-api`:

- `POST /models/add`
  - Adds a new `ModelRoot` to the current process registry.
  - Request body should contain `azName`, `visName`, and `version`.
  - Response should include the created model root.
  - Duplicate `azName` and invalid input must return a client error response.

- `GET /models/list`
  - Returns all currently known / loaded model roots.
  - Response order should be deterministic.

Keep HTTP parsing/serialization concerns inside `vedenemo-web-api`.

### Tests

Add command-line runnable tests for both endpoints.

Minimum coverage:

- adding a valid model succeeds
- listing models includes the added model
- duplicate `azName` is rejected
- case-only duplicate `azName` is rejected
- blank `visName` is rejected
- invalid version is rejected
- version leading zeroes are normalized

The tests must run through the existing backend GitHub Actions workflow via:

```bash
mvn -B clean verify
```

### Architecture Documentation

After successful implementation and test execution, update
`docs/architecture_doc.md` in the same change.

The update should document only the concrete implementation that exists after
the task is complete:

- `ModelRoot`
- model registry
- new HTTP endpoints
- relevant runtime flow
- any new dependencies or test infrastructure

### Test Dependencies

It is acceptable to introduce JUnit 5 and a small HTTP client or HTTP testing
dependency for endpoint tests, scoped to test modules only.

### Planning Status

All planning questions are resolved. This task is ready to move to execution
when selected as the current task.

## Refactor ModelStorage To Use ModelRoot

Status: executed. Full task text retained here for history.

### Goal

Align the remaining skeleton storage path with the concrete `ModelRoot` model
type while preserving the existing storage SPI, in-memory adapter, command
executor wiring, CLI wiring, and application composition root.

The earlier `VedenemoModel` placeholder should no longer be the stored model
type after this refactor.

### Scope

- Change `ModelStorage` to save and load `ModelRoot`.
- Update `InMemoryModelStorage` to store `ModelRoot`.
- Keep `ModelStorage` and `InMemoryModelStorage` as functional concepts for
  following phases.
- Keep `CommandExecutor` constructor wiring intact unless a compile-safe minimal
  signature adjustment is required.
- Keep `VedenemoApp.createCommandExecutor()` and CLI startup behavior working.
- Remove `VedenemoModel` only if no references remain after the refactor.
- Update `docs/architecture_doc.md` after implementation so it reflects the
  current concrete implementation.

### Constraints

- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak web, JSON, Javalin, or adapter types into core or SPI.
- Keep explicit constructor wiring.
- Do not add durable persistence yet.
- Do not change the existing ModelRoot HTTP endpoints unless required by the
  refactor.

### Tests / Verification

At minimum, run:

```bash
mvn -B clean verify
```

If practical, add focused tests for `InMemoryModelStorage` storing and loading
`ModelRoot` instances.

### Planning Notes

- `ModelStorage` currently stores `VedenemoModel`.
- `InMemoryModelStorage` currently stores `VedenemoModel`.
- `CommandExecutor` currently depends on `ModelStorage`, but does not yet use it
  for real command behavior.
- The active model API flow already uses `ModelRoot` and `ModelRegistry`.

### Planning Status

Ready to move to `tasks/current-task.md` when this refactor should be executed.


## Create VEntity and VAttribute classes into vedenemo-model-api

Status: executed. Full task text retained here for history.

### Goal

Add the first model-structure classes that will later be bound under
`ModelRoot`.

This task should introduce domain model types only. Do not add REST endpoints,
UX changes, persistence, parser behavior, or model-root binding yet.

### Domain Model Changes

Add `Versionable`, an abstract base class for model elements with lifecycle
version metadata:

- `activeSince`: required `ModelVersion` since which the element is considered
  active.
- `deprecatedSince`: optional `ModelVersion` since which the element is
  considered deprecated.

When `VEntity` or `VAttribute` is created for a model, `activeSince` is expected
to come from the current `ModelVersion` of the owning `ModelRoot`. The actual
binding to `ModelRoot` is not implemented in this task; callers pass the
`ModelVersion` explicitly for now.

Add `DataType`, a Java enum:

```java
public enum DataType {
    TEXT,
    NUMERIC,
    URL,
    DATA
}
```

Add `VAttribute`, a model attribute class extending `Versionable`, with these
fields:

- `azName`: ASCII Vedenemo attribute identifier. Preserve the original case as
  entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the attribute. UTF-8 string. Must be non-blank.
  Uniqueness is not enforced (but in practise is expected to be unique inside
  a hosting entity in order to make any sense).
- `type`: `DataType`.

`VAttribute` should not know which entity hosts it and should not enforce
attribute-name uniqueness by itself. Attribute `azName` uniqueness belongs to
the hosting `VEntity`.

Add a `VEntity` class with these fields:

- `azName`: ASCII Vedenemo entity identifier. Preserve the original case as
  entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the entity. UTF-8 string. Must be non-blank.
  Uniqueness is not enforced (but in practise is expected to be unique inside
  a model in order to make any sense).
- `attributes`: ordered collection of `VAttribute` instances belonging to the
  `VEntity`. It must preserve original insertion order.

`VEntity` extends `Versionable`.

`VEntity` should provide explicit operations for managing attributes:

- add a `VAttribute`
- remove a `VAttribute` by attribute `azName`
- remove a `VAttribute` by instance
- list attributes as a read-only copy in insertion order

`VEntity` must enforce attribute `azName` uniqueness case-insensitively.
`Example` and `example` conflict, but original submitted casing is preserved.

`VEntity` itself should not enforce entity-name uniqueness within a model.
Entity uniqueness will be handled later by the future container that binds
entities under `ModelRoot` or another model-level aggregate.

`VAttribute` and `VEntity` should be immutable after construction in this first
iteration. The exception is `VEntity`'s explicit attribute-management methods,
which may add and remove attributes while a model is under construction. Later
release/deprecation rules are out of scope for this task.

Implementation constraints:

- Put Vedenemo-owned model types in a pure JDK module, most likely
  `vedenemo-model-api`, unless implementation analysis finds a better existing
  boundary.
- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak Javalin, JSON-library, or HTTP types into model/core/SPI modules.
- Prefer small explicit classes.
- Reuse the existing `ModelRoot` `azName` validation rules. If practical,
  extract shared package-private validation helpers in `vedenemo-model-api` so
  `ModelRoot`, `VEntity`, and `VAttribute` do not duplicate the same low-level
  validation code.

### Tests / Verification

Add focused model API tests if practical.

Minimum test coverage:

- `VAttribute` accepts valid data.
- `VAttribute` rejects invalid `azName`.
- `VAttribute` rejects blank `visName`.
- `VAttribute` rejects missing `DataType`.
- `VEntity` accepts valid data.
- `VEntity` preserves attribute insertion order.
- `VEntity` rejects duplicate attribute `azName`.
- `VEntity` rejects case-only duplicate attribute `azName`.
- `VEntity` can remove an attribute by `azName`.
- `VEntity` can remove an attribute by instance.
- `VEntity.attributes()` returns a read-only copy.
- lifecycle versions reject invalid combinations if lifecycle ordering is
  invalid.

At minimum, run:

```bash
mvn -B clean verify
```

### Architecture Documentation

After implementation and successful verification, update
`docs/architecture_doc.md` in the same change.

The update should document only the concrete implementation that exists after
the task is complete:

- `Versionable`
- `DataType`
- `VAttribute`
- `VEntity`
- `VEntity` attribute ordering and uniqueness behavior

### Resolved Planning Decisions

- `deprecatedSince` is optional.
- `activeSince` is required.
- `activeSince` is supplied from the current `ModelVersion` of the owning
  `ModelRoot` when entities or attributes are added to a model.
- When present, `deprecatedSince` must be strictly later than `activeSince`.
  Equal versions are invalid.
- `VEntity` removal should support both `azName` and `VAttribute` instance.
- `VEntity.attributes()` should expose only a read-only `List<VAttribute>` copy.
- `VAttribute` and `VEntity` should be immutable after construction, except for
  explicit `VEntity` attribute add/remove methods during model construction.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Added `Versionable`, `DataType`, `VAttribute`, and `VEntity` to
  `vedenemo-model-api`.
- Extracted shared model text validation so `ModelRoot`, `VEntity`, and
  `VAttribute` use the same `azName` and `visName` rules.
- Made `ModelVersion` comparable so lifecycle ordering can be validated.
- Added focused model API tests for valid data, invalid names, blank display
  names, missing data type, attribute order, duplicate attributes, removal
  operations, read-only snapshots, and invalid lifecycle versions.
- Updated `docs/architecture_doc.md` to reflect the current concrete
  implementation.
- `mvn -B clean verify` passed.

## Create Session concept, bind it to CommandExecutor, and start new session via command-line interface

Status: executed. Full task text retained here for history.

### Goal

Introduce a first `Session` concept and make command execution session-aware,
then expose a minimal CLI flow that starts a session, shows the session UUID,
accepts an interactive prompt, and cleans the session up on exit.

This is a planning task. All planning questions are resolved, and the task is
ready to move to execution when selected.

### Current Implementation Context

- `vedenemo-core` currently contains `Command`, `NoOpCommand`, and
  `CommandExecutor`.
- `CommandExecutor` currently holds `ModelStorage` but does not yet perform real
  command behavior.
- `vedenemo-cli` currently starts in-process and directly constructs a
  `CommandExecutor` with `InMemoryModelStorage`.
- `vedenemo-web-api` currently exposes only model HTTP endpoints. There is no
  HTTP session API and no CLI HTTP client yet.

### Proposed Domain Model

Add a Vedenemo-owned `Session` concept in `vedenemo-core`.

Initial responsibilities:

- Each session has a unique UUID created when the session starts.
- The session tracks executed `Command` instances in execution order.
- The tracked command history must make future undo possible by walking
  executed commands backward in exact reverse order.
- The session tracks the model currently selected for modification as
  `Optional<String>` containing the selected `ModelRoot.azName`.
- Later phases may add dirty status, last save time, persisted session state, or
  richer metadata. These are out of scope for the first implementation.
- Command history should be exposed as an immutable execution-order snapshot and
  also as an immutable reverse-order snapshot prepared for future undo logic.

### Command Executor Binding

`CommandExecutor` should become session-aware.

Planning intent:

- A `CommandExecutor` instance should be bound to one active `Session`.
- The executor should execute commands in the context of its bound `Session`.
- Executed commands should be recorded into that session.
- `CommandExecutor` is primarily a backend-side concept. Commands invoked via
  HTTP API should be tracked through backend session-bound executors.
- The implementation should stay small and deterministic.
- No dependency injection framework should be introduced.
- No third-party dependencies should be added to `vedenemo-core`.

Implementation planning note:

- A backend session registry/manager should create the `Session` and the
  session-bound `CommandExecutor` together, so later HTTP command endpoints can
  resolve the executor by session UUID.

### Session Lifecycle

Add lifecycle operations for:

- create/start session
- look up an active session by UUID for backend HTTP request handling
- detach/end session
- remove session from the active session registry when ended

Planning intent:

- A process-local in-memory session registry is enough for this phase.
- Durable session persistence is out of scope.
- Distributed runtime behavior is out of scope.
- Session UUIDs should use JDK `UUID`.
- Attach-to-existing-session behavior is out of scope for the first CLI
  iteration. The CLI creates a new session on start and removes it on exit.

### CLI Behavior

`VedenemoCli` should be able to:

- Start as a standalone command-line application.
- Connect to a running Vedenemo HTTP API backend.
- Create a new backend session at startup through the HTTP API.
- Print that a session with UUID `<uuid>` was created or attached.
- Show the prompt:

```text
VedenemoCli>
```

- When the user presses Enter on an empty line, echo an empty line and show a
  new prompt.
- Support the single command `exit`.
- On `exit`, call the HTTP API to detach/end the session and remove it from the
  active backend session registry.
- Provide an implementation skeleton that can later support more CLI commands
  and multi-step interaction sequences.
- If practical, register a JVM shutdown hook so Ctrl+C attempts best-effort
  HTTP session cleanup before process exit.

### Backend Access Boundary

The CLI should connect to the backend through HTTP in this task.

- Option A: the CLI uses an in-process backend for now, through
  `vedenemo-app`/`CommandExecutor`/session registry wiring. This keeps the task
  narrow and avoids adding HTTP client behavior to the CLI.
- Option B: add HTTP session endpoints in `vedenemo-web-api`, probably through
  a new `SessionResource`, and make the CLI call a running web API process.
  This is closer to remote backend wording but broadens scope to HTTP API,
  serialization, CLI configuration, connection errors, and web tests.

Resolved decision: use Option B now. Add a `SessionResource` or equivalent HTTP
resource in `vedenemo-web-api`, and make `VedenemoCli` call a running backend
HTTP API process.

The first HTTP surface should stay minimal:

- `POST /sessions/start` creates a new backend session and returns the session
  UUID.
- `DELETE /sessions/{uuid}` ends/removes the backend session.
- No HTTP command endpoint is included in this task.
- Optional ping/lookup endpoint may be added only if needed for tests or CLI
  robustness.

### Suggested Module Placement

- Put `Session` and session lifecycle logic in `vedenemo-core`.
- Keep the process-local session registry/manager in `vedenemo-core` unless
  later persistence needs a SPI.
- Keep HTTP session endpoints, JSON mapping, and HTTP errors in
  `vedenemo-web-api`.
- Keep CLI interaction code and HTTP client calls in `vedenemo-cli`.
- The CLI should read the backend base URL from `VEDENEMO_API_BASE_URL`, with
  default `http://127.0.0.1:8080`.
- Keep application wiring in `vedenemo-app`, `vedenemo-web-api`, or
  `vedenemo-cli`.
- Do not put CLI or HTTP framework types into `vedenemo-core`.

### Tests / Verification

Add focused backend tests where practical.

Minimum intended coverage:

- creating a session assigns a UUID
- ending/removing a session removes it from the active session registry
- command execution records commands in the active session
- command history is exposed in deterministic execution order or reverse order
  as needed for future undo
- selected model starts empty if no model is selected
- selected model can be changed if this is implemented in the first pass
- HTTP session creation returns a UUID
- HTTP session end removes the backend session
- CLI command loop can be smoke-tested without hanging, if practical

At minimum, run:

```bash
mvn -B clean verify
```

CLI smoke verification should be added if practical, but must not make the test
suite hang on interactive input.

### Architecture Documentation

After implementation and successful verification, update
`docs/architecture_doc.md` in the same change.

The update should document only concrete implementation that exists after the
task is complete:

- `Session`
- session registry or manager
- how `CommandExecutor` relates to `Session`
- HTTP session endpoints
- CLI startup/session lifecycle flow through HTTP

### Resolved Planning Decisions

- The CLI should use HTTP API access in this task.
- Add backend HTTP session endpoints now, using `SessionResource` or an
  equivalent resource in `vedenemo-web-api`.
- Use `POST /sessions/start` for session creation.
- Use `DELETE /sessions/{uuid}` for session cleanup.
- The CLI reads the backend base URL from `VEDENEMO_API_BASE_URL`, defaulting to
  `http://127.0.0.1:8080`.
- `CommandExecutor` remains primarily backend-side and tracks commands invoked
  through HTTP API.
- Store selected model as `Optional<String>` containing model `azName`.
- Bind each `CommandExecutor` instance to one active `Session`.
- First CLI iteration only needs create-on-start and remove-on-exit.
- Attach-to-existing-session behavior is out of scope.
- Command history should expose immutable execution-order and reverse-order
  snapshots.
- Do not add an HTTP command endpoint for `NoOpCommand` in this task. Implement
  only session lifecycle endpoints plus backend-side executor binding.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Added `Session` and `SessionManager` to `vedenemo-core`.
- Bound `CommandExecutor` to one active `Session` and made `execute` record
  commands in that session.
- Added immutable execution-order and reverse-order command history snapshots.
- Added selected model tracking as optional model `azName`.
- Added `POST /sessions/start` and `DELETE /sessions/{uuid}` in
  `vedenemo-web-api`.
- Updated `vedenemo-cli` to create and clean up backend sessions through the
  HTTP API using `VEDENEMO_API_BASE_URL`, defaulting to
  `http://127.0.0.1:8080`.
- Added focused core, CLI, and web API tests.
- Updated `docs/architecture_doc.md` to reflect the current concrete
  implementation.
- `mvn -B clean verify` passed.
- Live local smoke test for session start/delete passed after running outside
  the sandbox because sandbox socket binding was blocked.

## Adding support for adding new models and listing existing models to VedenemoCli

Status: executed. Full task text retained here for history.

### Goal

Add first useful model-management commands to `VedenemoCli` while preserving the
existing HTTP-backed session startup and cleanup behavior.

The CLI should be able to:

- list existing models from the backend
- add a new model through the backend HTTP API
- attach the current CLI session to one listed model
- detach the current CLI session from the attached model
- show help for available commands

This is a planning task. All planning questions are resolved, and the task is
ready to move to execution when selected.

### Current Implementation Context

- `VedenemoCli` currently starts an HTTP-backed backend session using
  `POST /sessions/start`.
- The CLI currently supports only empty lines and `exit`.
- The CLI already reads `VEDENEMO_API_BASE_URL`, defaulting to
  `http://127.0.0.1:8080`.
- The web API already exposes:
  - `POST /models/add`
  - `GET /models/list`
  - `POST /sessions/start`
  - `DELETE /sessions/{uuid}`
- `Session` already stores selected model as `Optional<String>` containing
  model `azName`, but no HTTP endpoint currently updates that selected model.

### CLI Commands

All previously available CLI behavior must keep working:

- startup creates a backend session
- empty line returns a new prompt
- `exit` cleans up the backend session and exits

Add these new commands.

#### `list`

Lists all currently added / loaded models from the backend as a numbered list.

Each row should show:

- running number `N`
- visible name / `visName`
- ASCII name / `azName`
- version, if useful and already returned by the backend

Example output shape:

```text
1. Example Model (Example_Model) version 1.0.0
2. Sales Model (Sales_Model) version 1.0.0
```

If there are no models, print a clear message such as:

```text
No models available.
```

The numbering should be deterministic and based on the backend list order.

#### `attach [N | azName]`

Associates the current CLI session with an existing model.

Supported forms:

- `attach N` attaches by the running number from the latest model list.
- `attach azName` attaches by model `azName`.
- `attach` with no argument asks the user for a number or `azName`.

Expected behavior:

- If there are no models, report that there are no models to attach.
- If a numeric argument does not match any listed model number, report a clear
  error.
- If an `azName` argument does not match any existing model, report a clear
  error.
- After successful attach, the prompt changes to:

```text
VedenemoCli[azName]>
```

- Successful attach must update backend `Session.selectedModelAzName` through
  HTTP.
- `attach N` always refers to the most recent `list` output. If no list has been
  loaded yet, report that the user must run `list` first or attach by `azName`.

#### `detach`

Detaches the current CLI session from the previously attached model.

Expected behavior:

- If a model is attached, clear the attached model and return the prompt to:

```text
VedenemoCli>
```

- If no model is attached, print a clear message that there is no attached
  model.
- Successful detach must update backend `Session.selectedModelAzName` through
  HTTP.

Only the correctly spelled `detach` command is supported. Do not add `detatch`
as an alias.

#### `add`

Adds a new model through the backend HTTP API.

Interactive flow:

1. Ask for `visName`.
2. Generate a valid ASCII `azName` suggestion from the entered `visName`.
3. Ask for `azName`, showing the suggestion.
4. If the user presses Enter without typing a replacement, use the suggestion.
5. If the user types a replacement, use the typed value.
6. Create the model with version `1.0.0`.
7. After successful creation, automatically attach the CLI session to the new
   model, update backend `Session.selectedModelAzName`, and update the prompt
   to `VedenemoCli[azName]>`.

The `add` command should handle backend validation errors, including duplicate
`azName`, and show a readable message without exiting the CLI.

Suggested `azName` generation rule for planning:

- transliterate only by simple ASCII filtering for now; no third-party
  dependency
- split the visual name into ASCII letter runs
- join runs with underscores
- ensure the result starts with an ASCII letter
- if no valid ASCII letter exists, fall back to a prompt asking the user to
  enter `azName` manually
- preserve the user's final typed casing

This should align with the existing `ModelRoot` rule: starts with an ASCII
letter and then contains only ASCII letters and underscores. Digits and hyphens
are not allowed.

#### `help`

Lists all available commands with short explanations.

Minimum commands to show:

- `list`
- `add`
- `attach [N | azName]`
- `detach`
- `help`
- `exit`

### Backend / HTTP Scope

The existing model endpoints are enough for listing and adding:

- `GET /models/list`
- `POST /models/add`

Add backend session-selection endpoints in this task so attach/detach are
reflected in backend `Session.selectedModelAzName`.

Suggested endpoint shape:

- `PUT /sessions/{uuid}/selected-model`
  - request body contains `azName`
  - validates that the session exists
  - validates that the model exists in the process-local `ModelRegistry`
  - updates `Session.selectedModelAzName`
- `DELETE /sessions/{uuid}/selected-model`
  - validates that the session exists
  - clears `Session.selectedModelAzName`

The exact request/response DTO shape can be chosen during implementation, but
HTTP and JSON details must stay in `vedenemo-web-api`.

### Suggested CLI Structure

The current `VedenemoCliApp` has a simple prompt loop. This task can extend that
structure, but should keep the code testable without hanging on stdin.

Suggested implementation direction:

- Add a model HTTP client abstraction in `vedenemo-cli`, similar to
  `SessionClient`.
- Extend the session HTTP client abstraction to support selecting and clearing
  the selected model.
- Add small CLI command handling methods/classes for `list`, `add`, `attach`,
  `detach`, `help`, and `exit`.
- Keep JSON parsing/writing in the CLI small and explicit. If possible, use JDK
  APIs and simple structured parsing compatible with the known backend response
  shape rather than adding a new CLI JSON dependency.
- Keep backend HTTP framework and Jackson types out of the CLI.

### Tests / Verification

Add focused CLI tests where practical.

Minimum intended coverage:

- `help` prints all commands.
- `list` prints an empty-list message when there are no models.
- `list` prints numbered models when models exist.
- `attach N` attaches to the numbered model and updates the prompt.
- `attach azName` attaches to the named model and updates the prompt.
- `attach` with no argument asks for a model identifier.
- `attach N` without a previous `list` prints a clear message and does not
  fetch automatically.
- successful attach updates backend selected model state.
- invalid `attach` input prints a clear message and keeps the previous prompt.
- `detach` clears the prompt when a model is attached.
- `detach` with no attached model prints a clear message.
- successful detach clears backend selected model state.
- `add` prompts for `visName`, suggests `azName`, creates model version
  `1.0.0`, attaches to the created model, and updates backend selected model
  state.
- backend validation failure during `add` is reported without exiting the CLI.
- backend tests verify session selected-model set/clear endpoints.
- `exit` still cleans up the backend session.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a local smoke test with the backend JAR and the CLI command
flow. Any smoke test must not require manual input in CI.

### Documentation

After implementation:

- Create a separate CLI reference document, preferably `docs/cli-reference.md`.
- Document `list`, `add`, `attach`, `detach`, `help`, and `exit`.
- Include examples of adding a model, listing models, attaching by number,
  attaching by `azName`, detaching, and exiting.
- Update `README.md` with a short link to the CLI reference instead of
  duplicating all command details there.

### Architecture Documentation

If implementation adds new HTTP endpoints or changes current component
responsibilities, update `docs/architecture_doc.md` in the same change.

At minimum, update it if:

- new session model-selection endpoints are added
- CLI command handling becomes a distinct component worth documenting
- CLI/backend runtime flow materially changes

### Resolved Planning Decisions

- `attach` and `detach` must update backend `Session.selectedModelAzName`
  through HTTP endpoints.
- Only `detach` is supported. Do not add typo alias `detatch`.
- For `attach N`, `N` always refers to the most recent `list` output.
- `attach N` must not fetch the model list automatically if no list exists.
- After `add` creates a model, auto-attach must update backend selected model
  state as well as local CLI prompt state.
- Create a separate CLI reference document now and link to it from `README.md`.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Added CLI model-management commands: `list`, `add`, `attach`, `detach`, and
  `help`.
- Added CLI HTTP model client support for `GET /models/list` and
  `POST /models/add`.
- Extended CLI session HTTP support for selecting and clearing the backend
  session selected model.
- Added backend selected-model endpoints:
  - `PUT /sessions/{uuid}/selected-model`
  - `DELETE /sessions/{uuid}/selected-model`
- Added focused CLI and web API tests for the new command and endpoint
  behavior.
- Added `docs/cli-reference.md`, linked it from `README.md`, and updated the
  current implementation architecture document.
- `mvn -B clean verify` passed.

## Taking Command concept into use and implementing the first command

Status: executed. Full task text retained here for history.

### Goal

`vedenemo-core` already has placeholder command concepts:

- `Command`
- `NoOpCommand`
- `CommandExecutor`
- session-bound command history in `Session`

This task should take the command concept into real use by adding the first
model-changing command: create a new `VEntity` in the currently selected model.

This is still a planning task. The current implementation has `ModelRoot`
metadata and standalone `VEntity` / `VAttribute` classes, but `ModelRoot` does
not yet contain entities. This task will extend `ModelRoot` so it directly owns
entities in this first iteration.

### Current Implementation Context

- `ModelRoot` currently contains only `azName`, `visName`, and `ModelVersion`.
- `VEntity` exists and can hold ordered attributes, but no current model object
  owns `VEntity` instances.
- `ModelRegistry` stores process-local `ModelRoot` instances in insertion order.
- `ModelStorage` and `InMemoryModelStorage` store and load `ModelRoot`.
- `Session` stores the currently selected model as `Optional<String>` containing
  `ModelRoot.azName`.
- `Session` records executed `Command` instances and can expose command history
  in reverse order for future undo behavior.
- `CommandExecutor` is bound to one `Session`, but currently only records a
  command and does not mutate a model.
- `vedenemo-web-api` already has model add/list endpoints and session lifecycle
  / selected-model endpoints.
- `VedenemoCli` already has context-dependent `add`: without a selected model it
  adds a model and attaches to it.

### Proposed Domain / Model Scope

Add the smallest concrete structure needed for a model to own entities.

- Extend `ModelRoot` so it directly contains and manages ordered `VEntity`
  instances.
- Add explicit operations such as `addEntity(VEntity)`,
  `removeEntity(String azName)`, `removeEntity(VEntity)`, and `entities()`.
- Preserve insertion order and enforce entity `azName` uniqueness
  case-insensitively inside one model.
- `entities()` should expose a read-only `List<VEntity>` copy in insertion
  order.
- Entity `azName` uniqueness is enforced case-insensitively, while original
  casing is preserved.

The separate model aggregate/document option is intentionally not introduced in
this task. It may be revisited later if persistence or save/load boundaries need
a wrapper around `ModelRoot`.

### Command Architecture Goals

Add a `CreateEntityCommand`.

Initial command data:

- target model `azName`
- new entity `azName`
- new entity `visName`

The command should not carry `activeSince`; command execution derives
`activeSince` from the current version of the target model.

The created entity starts with no attributes.

Add a `DeleteEntityCommand` as the undo counterpart for `CreateEntityCommand`.

Expected command execution behavior:

- `CommandExecutor.execute(command)` validates and applies the command to the
  selected model.
- A successfully applied command is recorded into the session command history.
- A failed command is not recorded.
- `CommandExecutor` can undo the latest undoable command by applying the correct
  inverse command.
- Undo removes the latest created entity for this first command iteration.
- Undo removes the original command from active command history, so session
  command history represents the currently applied command state.
- If there is no command to undo, the executor reports that no undo operation
  was available.

`Session` currently exposes command history snapshots but does not support
popping the latest command. This task should add the minimal history operation
needed by undo while preserving read-only snapshot access for callers.

`DeleteEntityCommand` is only an internal inverse command for undo in this task.
It is not exposed as a user-executable CLI command yet.

### Backend / HTTP API Scope

Command execution is initiated through `vedenemo-web-api`.

Use command-specific endpoints for this phase.

Example shape:

```text
POST /sessions/{uuid}/commands/create-entity
```

This keeps request validation and JSON handling explicit, avoids reflection or
framework-specific polymorphic JSON behavior, and keeps the first implementation
small. A generic command envelope may be introduced later when there are enough
commands to justify it.

Undo endpoint:

```text
POST /sessions/{uuid}/commands/undo
```

Expected HTTP results:

- `200` when undo succeeds
- `304` when there is no command available to undo
- clear client error when session/model/command input is invalid
- unexpected status codes should remain visible to CLI users

### CLI Scope

Extend `VedenemoCli` command behavior:

- when no model is attached, `add` keeps its current behavior and creates a new
  model
- when a model is attached, `add` should add a new entity to the selected model
  by sending a backend command request
- add a new `undo` CLI command that asks the backend to undo the latest executed
  command for the active session

Proposed interactive `add` flow when a model is attached:

1. Ask for entity visible name.
2. Generate a valid ASCII `azName` suggestion using the same style as model add.
3. Ask for entity `azName`, showing the suggestion.
4. If the user presses Enter, use the suggestion.
5. Send the create-entity command to the backend.
6. On success, print:

```text
Entity <azName> added.
```

CLI-side HTTP request construction should stay separate from backend/core
command records for now. Executable behavior belongs on the backend. Keep the
HTTP field names stable and aligned with command record fields so future command
save/load support has a clear path.

### Serialization / Future Persistence Consideration

Commands are initially serialized only for HTTP traffic, but future CLI features
should be able to save and load both model data and executed command history to
external files.

Planning implications:

- command payloads should use stable Vedenemo-owned field names
- command types should have explicit stable type names if a generic command
  envelope is introduced
- avoid reflection-heavy JSON polymorphism
- keep command data separate from execution-only runtime dependencies
- command records should be pure JDK / Vedenemo-owned types

### Tests / Verification

Add focused core tests where practical:

- `CreateEntityCommand` adds an entity to the selected model.
- created entity uses the current model version as `activeSince`.
- created entity starts with no attributes.
- duplicate entity `azName` is rejected.
- failed command is not recorded in session history.
- successful command is recorded in session history.
- undo after create removes the created entity.
- undo with no undoable command reports no-op / unavailable.

Add focused web API tests:

- create-entity command endpoint succeeds for an active session with selected
  model.
- create-entity command endpoint rejects missing session.
- create-entity command endpoint rejects no selected model.
- create-entity command endpoint rejects invalid entity input.
- undo endpoint succeeds after create.
- undo endpoint returns `304` when nothing can be undone.

Add focused CLI tests:

- `add` without an attached model keeps current model-add behavior.
- `add` with an attached model prompts for entity data and sends create-entity.
- duplicate or invalid entity errors are printed without exiting the CLI.
- `undo` prints success when backend undo succeeds.
- `undo` prints a clear message when backend returns `304`.
- unexpected undo status is shown to the user.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model
- add entity while attached
- undo
- exit

### Architecture Documentation

After implementation, update `docs/architecture_doc.md` in the same change if
this task changes current concrete architecture. It likely will, because it
introduces real command execution, model entity ownership, backend command
endpoints, undo behavior, and CLI command execution flow.

### Resolved Planning Decisions

- `ModelRoot` should directly own `VEntity` instances in this iteration.
- Do not introduce a separate model aggregate/document type yet.
- Use command-specific HTTP endpoints for this phase.
- Undo removes the original command from session command history, so active
  command history represents the currently applied state.
- `DeleteEntityCommand` is only an internal inverse command for undo in this
  task.
- CLI should keep HTTP request DTO construction separate from backend/core
  command records for now.
- The CLI success message after adding an entity is:

```text
Entity <azName> added.
```

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Extended `ModelRoot` to directly own ordered `VEntity` instances with
  case-insensitive entity `azName` uniqueness, remove operations, and read-only
  snapshot listing.
- Added `CreateEntityCommand`, internal `DeleteEntityCommand`, and `UndoResult`
  in `vedenemo-core`.
- Updated `CommandExecutor` to apply create-entity commands to the selected
  model, record only successful commands, and undo the latest create-entity
  command by removing the created entity and removing the original command from
  active history.
- Added session history support for peeking/removing the latest command.
- Wired `SessionManager` and `CommandExecutor` to the process-local
  `ModelRegistry` used by the web API.
- Added `POST /sessions/{uuid}/commands/create-entity` and
  `POST /sessions/{uuid}/commands/undo`.
- Added CLI command transport, context-dependent `add` for attached-model entity
  creation, and `undo`.
- Added focused model, core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed.

## Add VAttribute Commands And CLI Entity Context

Status: executed. Full task text retained here for history.

### Goal

Extend the existing command execution path so users can add `VAttribute` items
to an existing `VEntity` through `VedenemoCli`, using the backend HTTP API in
the same command-oriented style already used for adding entities to a model.

This task should also add the internal attribute removal operation needed to
undo attribute creation. User-visible attribute removal as a separate edit
operation is deferred until it can be recorded and undone as its own command.

### Current Implementation Context

- `ModelRoot` directly owns ordered `VEntity` instances.
- `VEntity` already owns ordered `VAttribute` instances and enforces attribute
  `azName` uniqueness case-insensitively.
- `VAttribute` already contains `azName`, `visName`, `DataType`, and lifecycle
  metadata from `Versionable`.
- `Session` stores the currently selected model as `Optional<String>`.
- `CommandExecutor` can execute `CreateEntityCommand` against the selected
  model and can undo it through internal `DeleteEntityCommand`.
- `vedenemo-web-api` exposes command-specific endpoints:
  - `POST /sessions/{uuid}/commands/create-entity`
  - `POST /sessions/{uuid}/commands/undo`
- `VedenemoCli` uses HTTP-backed sessions. `add` creates a model when detached
  and creates an entity when attached to a model.

### Domain / Model Scope

No new model types should be required for this task.

Use the existing `VEntity` attribute-management operations:

- add a `VAttribute`
- remove a `VAttribute` by attribute `azName`
- remove a `VAttribute` by instance
- list attributes as a read-only copy in insertion order

Expected behavior:

- Adding an attribute preserves insertion order inside the owning entity.
- Attribute `azName` uniqueness remains enforced case-insensitively inside one
  entity.
- Created attributes derive `activeSince` from the current version of the
  owning `ModelRoot`.
- Created attributes start with no additional metadata beyond current
  `VAttribute` fields.
- Removing an attribute removes the current attribute from the owning entity.
- Durable persistence, save/load formats, parser syntax, and UI visualization
  remain out of scope.

### Command Architecture Goals

Add `CreateAttributeCommand`.

Initial command data:

- target model `azName`
- target entity `azName`
- new attribute `azName`
- new attribute `visName`
- new attribute `DataType`

The command should not carry `activeSince`; command execution derives it from
the current version of the target model.

Add `DeleteAttributeCommand`.

Initial command data:

- target model `azName`
- target entity `azName`
- target attribute `azName`

Expected command execution behavior:

- `CommandExecutor.execute(command)` validates and applies
  `CreateAttributeCommand` to an entity in the selected model.
- `DeleteAttributeCommand` is the internal undo counterpart for
  `CreateAttributeCommand` in this task.
- `DeleteAttributeCommand` does not need to be user-executable or recorded as a
  separate command in this task.
- Successful commands are recorded in session command history.
- Failed commands are not recorded.
- Undo of `CreateAttributeCommand` removes the created attribute by deriving and
  applying the delete counterpart at undo time, then removes the original create
  command from active history.
- Undo always operates on the latest successfully executed command only. Treat
  session command history as a stack: undo pops the topmost command and cannot
  undo a command from the middle of the history.
- `CreateAttributeCommand` must retain enough target identity to derive its undo
  counterpart later:
  - target model `azName`
  - target entity `azName`
  - target attribute `azName`
- `DeleteAttributeCommand` does not need to be created or stored when the
  attribute is originally added. It can be constructed only when undo is
  actually requested.
- User-visible attribute deletion as a later edit operation is a separate
  concern. When introduced, it must be recorded as the latest executed command
  and must have enough undo data to restore the deleted attribute.

Resolved decision for this task:

- Keep `DeleteAttributeCommand` as the internal counterpart operation used for
  undoing `CreateAttributeCommand`.
- Keep undo support in this task focused on undoing newly created attributes.
- Defer user-visible attribute removal to a later task. That later task should
  introduce whatever command-result or undo-record structure is needed to retain
  the removed `VAttribute` and original insertion position.

### Backend / HTTP API Scope

Keep command execution in `vedenemo-web-api` behind command-specific endpoints.

Add:

```text
POST /sessions/{uuid}/commands/create-attribute
```

Suggested create-attribute request body:

```json
{
  "entityAzName": "Customer",
  "attributeAzName": "Email",
  "attributeVisName": "Email",
  "dataType": "TEXT"
}
```

If `dataType` is missing or blank, the backend should default it to `TEXT`.
If present, the backend should accept case-insensitive enum names and aliases
such as `text`, `number`, `url`, and `data`.

Expected HTTP behavior:

- `200` when command execution succeeds.
- `400` for invalid command input, no selected model, missing target entity,
  duplicate attribute `azName`, or unsupported data type.
- `404` for missing session.
- Existing `POST /sessions/{uuid}/commands/undo` should undo attribute creation
  in addition to entity creation.
- Do not add a user-facing delete-attribute command endpoint in this task.
- JSON parsing/serialization stays in `vedenemo-web-api`.
- Core command records remain pure JDK / Vedenemo-owned types.

### CLI Scope

Add a clear way to select an entity as the target for attribute operations.

Recommended CLI model:

- Keep the existing attached model prompt:

```text
VedenemoCli[Model_AzName]>
```

- Add an optional attached entity context and show it in the prompt:

```text
VedenemoCli[Model_AzName/Entity_AzName]>
```

Recommended commands:

- `entities`
  - Lists entities in the currently attached model as a numbered list.
  - Requires an attached model.
- `entity [N | azName]`
  - Selects the target entity for attribute operations.
  - `entity N` uses the most recent `entities` output.
  - `entity azName` resolves by entity `azName`.
  - `entity` with no argument asks for a number or `azName`.
- `entity detach`
  - Clears the selected entity while keeping the model attached.
- `attributes`
  - Lists attributes in the selected entity.
  - Requires both an attached model and selected entity.
- `attr add`
  - Adds a new attribute to the selected entity.
  - Prompts for visible name, suggests `azName`, asks for final `azName`, then
    asks for `DataType`.
  - If `DataType` is left blank, default to `TEXT`.
  - Accept case-insensitive data type aliases such as `text`, `number`, `url`,
    and `data`; normalize them to the existing `DataType` enum values.

Expected CLI behavior:

- `add` should keep its current meanings:
  - detached from a model: add a model
  - attached to a model but no entity selected: add an entity
- Attribute creation should use `attr add` instead of adding a third contextual
  meaning to `add`. This keeps the prompt behavior predictable and avoids
  surprising users when an entity is selected.
- If `attr add` submits an attribute `azName` that overlaps an existing
  attribute name case-insensitively, the backend should reject the command with
  a clear `400` validation response and the CLI should print a clear failure
  message without exiting.
- Recommended duplicate-name CLI flow:
  1. User runs `attr add`.
  2. CLI asks for attribute visible name, suggests an `attributeAzName`, asks
     for the final `attributeAzName`, then asks for `DataType`.
  3. CLI sends the create-attribute command to the backend.
  4. Backend detects the duplicate inside the target `VEntity` and returns a
     clear validation error such as `attribute azName must be unique within
     VEntity`.
  5. CLI prints `Attribute was not added: <backend error>.`
  6. CLI keeps the current model/entity context and returns to the prompt.
  7. The failed command is not recorded in backend session history, so `undo`
     is unaffected by the failed add.
- Do not automatically re-prompt inside the same `attr add` interaction in this
  task. The user can run `attributes` to inspect the current state or run
  `attr add` again with another name.
- `detach` should continue clearing the selected model. It should also clear
  any selected entity.
- `undo` should keep calling the backend undo endpoint.
- Undo should always target only the latest successfully executed command in the
  backend session history.
- Successful attribute creation prints:

```text
Attribute <azName> added.
```

- Successful entity detach prints:

```text
Entity detached.
```

- `help` should include the new entity and attribute commands.

### Read / Listing API Scope

The CLI needs a way to list entities and attributes before selecting targets by
number.

Preferred narrow endpoints:

```text
GET /models/{modelAzName}/entities
GET /models/{modelAzName}/entities/{entityAzName}/attributes
```

Expected behavior:

- Entity listing returns deterministic insertion order from `ModelRoot`.
- Attribute listing returns deterministic insertion order from `VEntity`.
- Attribute listing should include `DataType` and lifecycle version fields so
  CLI output can show the current attribute state.
- Entity and attribute response DTOs should include visible names and ASCII
  names.
- These listing endpoints are read-only model API endpoints, not command
  endpoints.

Alternative for review:

- Return entity and attribute summaries from existing model list responses.
  This is likely less focused and may make `/models/list` too heavy for the
  current phase.

### Serialization / Future Persistence Consideration

Command payloads should continue to use stable Vedenemo-owned field names:

- `modelAzName`
- `entityAzName`
- `attributeAzName`
- `attributeVisName`
- `dataType`

Avoid framework-specific polymorphic command serialization. If a generic command
envelope is introduced later, give command types stable explicit names.

### Tests / Verification

Add focused model/core tests where practical:

- `CreateAttributeCommand` adds an attribute to an entity in the selected model.
- Created attribute uses the target model version as `activeSince`.
- Created attribute has the requested `DataType`.
- Duplicate attribute `azName` is rejected.
- Missing target entity is rejected.
- Failed create-attribute commands are not recorded in session history.
- Successful create-attribute commands are recorded in session history.
- Undo after create-attribute removes the created attribute.
- Undo operates only on the latest command in the session history stack.
- `DeleteAttributeCommand` removes an existing attribute when used internally as
  the undo counterpart for `CreateAttributeCommand`.

Add focused web API tests:

- create-attribute command endpoint succeeds for an active session with selected
  model and existing entity.
- create-attribute rejects missing session.
- create-attribute rejects no selected model.
- create-attribute rejects missing entity.
- create-attribute rejects invalid attribute input.
- create-attribute rejects unsupported data type.
- create-attribute defaults missing or blank data type to `TEXT`.
- create-attribute accepts case-insensitive data type aliases.
- undo endpoint succeeds after create-attribute.
- entity listing endpoint returns created entities in order.
- attribute listing endpoint returns created attributes in order, including
  `DataType` and lifecycle version fields.

Add focused CLI tests:

- `entities` requires an attached model.
- `entities` prints numbered entity rows.
- `entity N` selects from the latest entity list.
- `entity azName` selects by name.
- `entity detach` clears entity context but keeps model context.
- prompt includes selected model and selected entity when both are selected.
- `attributes` requires selected entity.
- `attributes` prints numbered attribute rows with `DataType` and lifecycle
  version fields.
- `attr add` prompts for attribute data and sends create-attribute.
- `attr add` defaults blank data type input to `TEXT`.
- `attr add` accepts case-insensitive data type aliases.
- `attr add` prints `Attribute was not added: <backend error>.` when the
  backend rejects a duplicate attribute `azName`.
- failed `attr add` keeps the current selected model/entity context.
- failed `attr add` is not undoable because the backend must not record failed
  commands.
- backend validation failures are printed without exiting the CLI.
- `detach` clears both selected model and selected entity.
- `undo` reports successful undo of attribute creation.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model
- add entity
- select entity
- add attribute
- list attributes
- undo an attribute creation
- exit

### Documentation

After implementation:

- Update `docs/cli-reference.md` with entity selection and attribute commands.
- Update `docs/architecture_doc.md` because this task adds attribute command
  execution, attribute command endpoints, read/list endpoints for model
  internals, and a CLI entity context.

Before updating `docs/architecture_doc.md`, read and follow
`docs/architecture_doc_instructions.md`.

### Resolved Planning Decisions

- `DeleteAttributeCommand` is only the internal counterpart operation for
  undoing `CreateAttributeCommand` in this task.
- User-visible attribute deletion is deferred to a later edit-operation task.
- Undo is stack-based and always applies only to the latest successfully
  executed command.
- Clearing entity context uses `entity detach`.
- Attribute listing should show `DataType` and lifecycle version fields.
- Attribute data type input accepts case-insensitive aliases.
- Blank attribute data type input defaults to `TEXT`.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Promoted the task to `tasks/current-task.md` and executed it.
- Added `CreateAttributeCommand` and internal `DeleteAttributeCommand` to
  `vedenemo-core`.
- Updated `CommandExecutor` so create-attribute commands add `VAttribute`
  instances to an existing entity in the selected model.
- Kept undo stack-based; undo of create-attribute derives and applies the
  internal delete counterpart at undo time.
- Added `POST /sessions/{uuid}/commands/create-attribute`.
- Added read-only listing endpoints:
  - `GET /models/{modelAzName}/entities`
  - `GET /models/{modelAzName}/entities/{entityAzName}/attributes`
- Added CLI entity context with `entities`, `entity [N | azName]`,
  `entity detach`, `attributes`, and `attr add`.
- Added attribute data type normalization with blank/missing default `TEXT` and
  case-insensitive aliases.
- Added focused core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed during implementation.

## Improve CLI azName Suggestions And Undo Feedback

Status: executed. Full task text retained here for history.

### Goal

Improve two CLI usability details:

- `azName` suggestions should preserve useful numeric suffixes from visible
  names, so `Attribute 2` suggests `Attribute_2` instead of `Attribute`.
- `undo` output should describe what kind of operation was undone instead of
  only printing `Undo completed.`

### Current Implementation Context

- `VedenemoCliApp.suggestAzName(String)` currently keeps only ASCII letters and
  uses non-letter runs as separators.
- Current model validation rules for `azName` do not allow digits, but the CLI
  now suggests names for models, entities, and attributes.
- The user-visible desired suggestion `Attribute_2` implies that the model
  `azName` validation rules should allow digits after the first ASCII letter,
  or at minimum that the CLI suggestion behavior and validation rules must be
  reconciled before implementation.
- `CommandExecutor.undoLatest()` currently returns only `UndoResult.UNDONE` or
  `UndoResult.NOTHING_TO_UNDO`.
- `SessionResource` maps successful undo to a generic JSON response:

```json
{"status":"undone"}
```

- `HttpCommandClient.undo()` maps the response to `UndoCommandResult.UNDONE`.
- `VedenemoCliApp.undo()` prints only:

```text
Undo completed.
```

### azName Suggestion Scope

Update CLI `azName` suggestion behavior so numeric runs are preserved when they
occur after the suggested name already starts with an ASCII letter.

Recommended behavior:

- `Attribute 2` suggests `Attribute_2`.
- `Address Line 1` suggests `Address_Line_1`.
- `2 Attribute` does not start with `2`; it should suggest `Attribute` if there
  is a later ASCII-letter run.
- `Model 2026 Draft` suggests `Model_2026_Draft`.
- non-ASCII characters remain ignored for now, preserving the current
  no-transliteration behavior.
- repeated separators collapse to one underscore.
- trailing underscores are removed.

Implementation note:

- If digits are allowed in CLI suggestions, update the shared model `azName`
  validation rules so `ModelRoot`, `VEntity`, and `VAttribute` all allow ASCII
  digits after the first ASCII letter.
- Preserve the rule that `azName` must start with an ASCII letter.
- Keep hyphens invalid.

### Undo Feedback Scope

Make undo return enough Vedenemo-owned information for the CLI to print a
specific message.

Recommended core behavior:

- Replace or extend `UndoResult` so successful undo can describe the undone
  command kind and target.
- Keep `NOTHING_TO_UNDO` behavior for empty command history.
- Preserve stack-based undo: only the latest successfully executed command can
  be undone.
- Failed undo operations should still be reported as client-visible errors and
  should not silently remove command history.

Recommended backend response shape:

```json
{
  "status": "undone",
  "undoneCommand": "create-attribute",
  "modelAzName": "Example_Model",
  "entityAzName": "Customer",
  "attributeAzName": "Email"
}
```

For entity creation undo:

```json
{
  "status": "undone",
  "undoneCommand": "create-entity",
  "modelAzName": "Example_Model",
  "entityAzName": "Customer"
}
```

Recommended CLI messages:

```text
Undo completed: removed entity Customer from model Example_Model.
```

```text
Undo completed: removed attribute Email from entity Customer in model Example_Model.
```

`Nothing to undo.` remains unchanged for HTTP `304`.

### Backend / HTTP Scope

- Update `POST /sessions/{uuid}/commands/undo` to return the richer undo
  response when undo succeeds.
- Keep `304` when no command is available to undo.
- Keep clear client error responses when undo fails due invalid current model
  state.
- HTTP DTOs stay in `vedenemo-web-api`.
- Core undo result types stay pure JDK / Vedenemo-owned types.

### CLI Scope

- Update `suggestAzName` and its tests so digits after the first ASCII letter
  are preserved.
- Update `HttpCommandClient.undo()` and `UndoCommandResult` or equivalent CLI
  result type so the CLI can read the richer undo response.
- Update `VedenemoCliApp.undo()` to print operation-specific undo messages.
- Keep unexpected undo response statuses visible to CLI users.

### Tests / Verification

Add focused model/core tests where practical:

- `azName` validation accepts digits after the first ASCII letter.
- `azName` validation still rejects names that start with a digit.
- `azName` validation still rejects hyphens.
- undo after create-entity reports the undone operation kind and target.
- undo after create-attribute reports the undone operation kind and target.
- undo with no command still reports nothing to undo.

Add focused web API tests:

- undo after create-entity returns `undoneCommand` and entity target fields.
- undo after create-attribute returns `undoneCommand` and attribute target
  fields.
- undo with no command still returns `304`.

Add focused CLI tests:

- `Attribute 2` suggests `Attribute_2`.
- `Address Line 1` suggests `Address_Line_1`.
- leading digits are not used before the first ASCII letter.
- undo after entity creation prints an entity-specific message.
- undo after attribute creation prints an attribute-specific message that
  includes the entity and model names.
- `Nothing to undo.` remains unchanged.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model with numeric suffix
- add entity with numeric suffix
- select entity
- add attribute with numeric suffix
- undo attribute creation and verify specific undo output
- undo entity creation and verify specific undo output
- exit

### Documentation

After implementation:

- Update `docs/cli-reference.md` to document numeric `azName` suggestions and
  operation-specific undo output.
- Update `docs/architecture_doc.md` if undo result shape or model naming rules
  change in concrete architecture.

Before updating `docs/architecture_doc.md`, read and follow
`docs/architecture_doc_instructions.md`.

### Resolved Planning Decisions

- Digits after the first ASCII letter are valid for all `azName` values in
  `ModelRoot`, `VEntity`, and `VAttribute`.
- Use the recommended CLI undo wording.
- Include the model name in attribute undo output so non-interactive logs remain
  self-contained.
- Use HTTP/API slug names, such as `create-entity` and `create-attribute`, as
  stable command identifiers in backend undo responses.

### Command Naming Options Considered

The stable command naming scheme for undo responses was resolved after
considering these options:

Options:

- HTTP/API slug names, such as `create-entity` and `create-attribute`.
  - Pros: stable, language-neutral, already matches current command endpoint
    names, good for logs and future serialized command history.
  - Cons: not identical to Java type names, so core needs an explicit mapping.
- Java-like command type names, such as `CreateEntityCommand` and
  `CreateAttributeCommand`.
  - Pros: maps directly to current Java records and is easy to produce from
    core code.
  - Cons: leaks implementation naming into HTTP/log output and is less stable
    if Java classes are renamed.
- Enum-style stable constants, such as `CREATE_ENTITY` and `CREATE_ATTRIBUTE`.
  - Pros: simple to model in Java as an enum and stable if treated as API
    values.
  - Cons: less natural for URLs/logs and still needs formatting for display.
- Domain action names, such as `entity-created` and `attribute-created`.
  - Pros: describes the original command event clearly.
  - Cons: can be slightly confusing in an undo response unless documented,
    because the actual undo operation is removal.

Chosen decision:

- Use HTTP/API slug names, `create-entity` and `create-attribute`, in backend
  undo responses. They are explicit stable Vedenemo-owned command identifiers
  without tying the API to Java class names.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Promoted the task to `tasks/current-task.md` and executed it.
- Updated shared `azName` validation so `ModelRoot`, `VEntity`, and
  `VAttribute` allow ASCII digits after the first ASCII letter while still
  rejecting leading digits and hyphens.
- Updated CLI `azName` suggestions to preserve digit runs after the suggestion
  has started with an ASCII letter.
- Replaced coarse undo success reporting with richer core-owned `UndoResult`
  metadata containing stable HTTP/API slug command identifiers and target
  fields.
- Updated `POST /sessions/{uuid}/commands/undo` to return operation and target
  details on successful undo.
- Updated CLI undo output to print entity-specific and attribute-specific
  messages.
- Added focused model/core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed during implementation.

---

## Add CLI Save And Load For Vedenemo Script Files

Status: executed. Full task text retained here for history.

### Goal

Add `save` and `load` commands to `VedenemoCli` using a new text-based
Vedenemo Script file format with the `.vdos` extension.

`save` should export a selected model from the backend through HTTP, including
the model metadata, full model structure, and executed command history, then
write the result as UTF-8 text to a local file.

`load` should read a `.vdos` file from the local filesystem and send it to the
backend so the backend can recreate the model and command history.

### Recommended Direction

Use a script-like `.vdos` format, not a CLI-private data dump.

The file should be human-readable, UTF-8, and stable enough that users can
inspect and manually edit it when needed. It should also be backend-owned in
meaning: the CLI should move `.vdos` text between local disk and HTTP endpoints,
but should not contain the main replay/import rules.

This is preferable to a CLI-owned data dump because:

- command history already lives in backend session state, not in the CLI;
- future database persistence should happen behind backend adapter/API layers;
- load/replay behavior should be testable without the interactive CLI;
- a script-like format can become the common interchange format for local files,
  backend import/export, and later persistence migrations;
- stable HTTP/API command slugs already exist for undo metadata and can also be
  used as script command names.

The CLI can still own local user interaction:

- model target resolution from current attachment, list number, or `azName`;
- output path prompting and `.vdos` extension handling;
- UTF-8 file read/write;
- friendly messages for missing models, invalid parameters, missing files, and
  backend import/export errors.

### Current Starting Point

Current backend state:

- `ModelRegistry` stores current `ModelRoot` instances.
- `Session` records executed `Command` instances in order.
- `CommandExecutor` can execute and undo command records.
- HTTP currently exposes model listing, entity listing, attribute listing, and
  command execution endpoints.
- HTTP does not yet expose a model export endpoint that combines model metadata,
  nested model structure, and command history.
- HTTP does not yet expose a load/import endpoint.

Current CLI state:

- `VedenemoCliApp` tracks the attached model and latest `list` result.
- `attach [N | azName]` already resolves models by latest list number or
  case-insensitive `azName`.
- `save` can reuse the same target resolution behavior.
- `load` will need path handling and an HTTP client method for import.

### Proposed `.vdos` Shape

The first `.vdos` format should be line-oriented and intentionally simple.

Recommended shape:

```text
vedenemo-script 1

model azName=Example_Model visName="Example Model" version=1.0.0

commands
create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
create-attribute model=Example_Model entity=Customer attribute=Email visName="Email" dataType=TEXT activeSince=1.0.0

snapshot
entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
attribute entity=Customer azName=Email visName="Email" dataType=TEXT activeSince=1.0.0 deprecatedSince=null
```

Guidelines:

- The first line declares the format and version.
- Command names use stable HTTP/API slugs, such as `create-entity` and
  `create-attribute`.
- The command section is authoritative for import/replay.
- The snapshot section records the final model tree and is used for readability
  and validation after replay.
- Values are explicit key/value pairs so ordering is readable and extensions are
  possible.
- Text values are quoted and escaped when needed.
- `visName` values are stored as UTF-8 text.
- Model metadata is explicit.
- Entity and attribute lifecycle version fields are explicit in the script.
- The command list is the replayable source of how the model was built, while
  the snapshot is checked against the replay result.

Recommended initial implementation should include both:

- a model snapshot section for readable current state and validation;
- a command section that is authoritative for replay/import.

That makes the file useful for humans while still keeping command history
available for undo/replay-related development.

Settled format decision details:

- Command-script authoritative means the backend trusts and replays the command
  lines to recreate the model. A snapshot, if present, is only a readable
  comment/validation aid. This keeps one source of truth and fits undo/replay
  semantics well, but the import can only recreate states expressible by known
  commands.
- Snapshot authoritative means the backend trusts the final model tree section
  and command lines are informational history. This can load final state even if
  the command list is incomplete, but it risks drift between the snapshot and
  command history.
- Both command and snapshot sections can be present with commands authoritative:
  import replays commands and then validates that the resulting model matches
  the snapshot. This is stricter and more useful for humans, but requires more
  serializer/parser work in the first implementation.

### Backend / HTTP Scope

Add backend endpoints for export and import instead of requiring the CLI to
compose or interpret the full format.

Recommended endpoints:

```text
GET /models/{modelAzName}/script
POST /models/script
```

Recommended export behavior:

- Resolve `modelAzName` case-insensitively using existing model rules.
- Return `404` if the model is not found.
- Return UTF-8 `text/plain` `.vdos` content.
- Include model metadata, all entities, all attributes, lifecycle version
  fields, and command history for that model.
- Read command history from the model-level command journal, not from the
  current CLI session.

Recommended import behavior:

- Accept UTF-8 `.vdos` text.
- Parse and validate the script server-side.
- Recreate model state through Vedenemo-owned command/application services, not
  by CLI-local object construction.
- Return a structured result with imported model `azName`, command count, and
  whether the model was created, replaced, or rejected.
- If the script model `azName` already exists, reject first with a clear backend
  error. The CLI should then prompt the user and offer to retry with a new
  model `azName`.

Implementation note:

- `vedenemo-core` must remain pure JDK. If a reusable script parser/serializer is
  needed, keep it Vedenemo-owned and JDK-only in an appropriate Vedenemo module.
- `vedenemo-web-api` can handle HTTP content negotiation and DTO mapping.
- Avoid adding JSON/YAML/TOML as the `.vdos` format unless explicitly chosen;
  current direction is a Vedenemo-owned text script.

### CLI `save` Scope

Add command:

```text
save [N | azName] [outputPath]
```

Behavior:

- If an argument is provided, resolve it as a latest-list number or model
  `azName`, following `attach` conventions.
- If no argument is provided, save the currently attached model.
- If no argument and no attached model, print a friendly message and do nothing.
- If the argument is invalid or cannot be resolved, print a friendly message and
  do nothing.
- After resolving the model, request the `.vdos` text from the backend export
  endpoint.
- Suggest output file name `<modelAzName>.vdos` in the CLI start/current working
  directory.
- If an output path is provided inline, use it after extension and overwrite
  handling.
- If no output path is provided inline, let the user accept the default or enter
  another file name/path at the prompt.
- If the user gives no extension, append `.vdos`.
- If the user gives `.vdos`, do not duplicate it.
- Write the file as UTF-8 text.
- Report the saved path and model `azName`.
- Handle write failures gracefully.

Settled save command flow:

- Prompt-only flow:
  - `save`
  - `save Customer_Model`
  - `save 1`
  - After model resolution, CLI prompts:

```text
Output file [Customer_Model.vdos]:
```

  This is simpler interactively and avoids ambiguity, but is weaker for scripts
  and non-interactive usage.

- Inline optional output path flow:
  - `save`
  - `save Customer_Model`
  - `save 1`
  - `save Customer_Model ./exports/customer.vdos`
  - `save 1 ./exports/customer`

  This is better for repeatable shell usage. It requires parsing two optional
  arguments: the first model selector, the second output path. The output path
  should not be treated as a model selector.

- Hybrid flow:
  - Support inline output path when provided.
  - Otherwise prompt with the default `<modelAzName>.vdos`.

  This is the selected flow. It gives good interactive ergonomics while still
  supporting repeatable shell usage.

### CLI `load` Scope

Add command:

```text
load <path>
```

Behavior:

- Accept either a fully qualified path or a path relative to the CLI current
  working directory.
- If no extension is given, append `.vdos` for convenience.
- If the file is not found, print a friendly message and do nothing.
- Read file as UTF-8 text.
- Send the file content to the backend import endpoint.
- Print imported model `azName`, command count, and backend result.
- Automatically attach the CLI session to the imported model after a successful
  load.

### Command History Considerations

This task should explicitly decide where command history comes from.

Recommended:

- Introduce a model-level command journal so backend export is independent of
  CLI session lifetime.
- The backend export endpoint reads command history from the model-level journal
  and serializes only commands belonging to the target model.
- The CLI should not reconstruct command history by comparing model snapshots.
- Loaded commands are treated as persisted baseline state with no undo available
  from the load operation.

Current limitation:

- `Session.commandHistory()` is session-scoped. If multiple sessions can modify
  the same model, a pure session export may not contain the full model command
  history. This task should address that by adding model-level command history
  before relying on `.vdos` export.

### Duplicate / Existing Model Handling

The import path needs a deterministic behavior when the `.vdos` file contains a
model `azName` already present in the backend.

Options:

- reject with a clear message;
- prompt in the CLI to replace;
- import under a new `azName`;
- merge commands into the existing model.

Recommended first version:

- reject first with a clear backend error;
- CLI should then ask whether the user wants to retry import using a new model
  `azName`;
- defer replace and merge until edit and persistence semantics are clearer.

### Resolved Planning Decisions

- `.vdos` should include both command lines and a final model snapshot, with
  command lines authoritative. Import should replay commands and validate the
  resulting model against the snapshot. This makes manual edits slightly harder
  because both sections must stay consistent, but keeps replay semantics clean
  and the file readable.
- Backend export should use a model-level command journal instead of current
  session command history.
- Duplicate model `azName` on load should reject first, then offer the user a
  rename retry flow.
- After successful `load`, CLI should automatically attach to the loaded model.
- Imported command history should be treated as baseline state with no undo
  available from the load operation.
- `save` should use a hybrid output path flow: accept an inline output path when
  provided, otherwise prompt with editable default `<modelAzName>.vdos`.
- `load` should auto-resolve a missing extension by appending `.vdos`.
- Saving over an existing local file should prompt for overwrite confirmation.

### Tests / Verification

At minimum, implementation should add focused tests for:

- `.vdos` serialization includes model metadata, entities, attributes, lifecycle
  fields, and command lines.
- command history export includes only commands for the selected model.
- export of an unknown model returns a clear not-found response.
- import of a valid `.vdos` file creates the model and replays commands.
- import of a missing/invalid script reports a clear error.
- duplicate model import follows the resolved rule.
- CLI `save` with attached model uses the attached model when no argument is
  provided.
- CLI `save N` resolves from the latest model list.
- CLI `save azName` resolves case-insensitively.
- CLI output path handling appends `.vdos` only when needed.
- CLI `load` handles missing files gracefully.
- CLI `load` sends UTF-8 file content to the backend.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a local backend plus CLI smoke test:

- create a model with at least one entity and one attribute;
- run `save`;
- verify a `.vdos` file is written as UTF-8 text;
- start a clean backend or use an empty model name;
- run `load`;
- verify the model, entities, attributes, and command count are available.

### Documentation

After implementation:

- Update `docs/cli-reference.md` with `save` and `load` usage.
- Document `.vdos` file naming behavior and extension handling.
- Document the initial `.vdos` text format with a short example.
- Update `docs/architecture_doc.md` if new backend endpoints, script
  serialization components, command history ownership, or load/import flows are
  added.

Before updating `docs/architecture_doc.md`, read and follow
`docs/architecture_doc_instructions.md`.

### Open Questions

No open planning questions remain.

### Completion Notes

- Promoted the task to `tasks/current-task.md` and executed it.
- Added backend-owned `.vdos` script import/export support in core.
- Added model-level command journaling for model-targeting commands.
- Added backend HTTP script export/import endpoints.
- Added CLI `save [N | azName] [outputPath]` and `load <path>`.
- Implemented UTF-8 file I/O, `.vdos` extension handling, overwrite
  confirmation, duplicate-load rename retry, and auto-attach after load.
- Treated loaded commands as baseline state with no current-session undo stack
  entries.
- Updated CLI and architecture documentation.
- Added focused core, web API, and CLI tests.
- `mvn -B clean verify` passed during implementation.

## Add UX Model Selector And Refresh

Status: executed. Short historical task entry added after implementation.

### Goal

Add a UX control for selecting the active model from backend data and refreshing
the available model list.

### Completion Notes

- Added a model dropdown populated from the backend at page load.
- Added a `Refresh model list` action to reload available models.
- Changed the model selector label to `Select model`.
- Committed as `34f4c1f Add UX model selector with refresh`.

## Add UX Model Event Connection And PlantUML Text Output

Status: executed. Short historical task entry added after implementation.

### Goal

Allow the UX to connect to backend model-change events and show the selected
model as a PlantUML class-diagram text representation.

### Completion Notes

- Added backend `/models/events` WebSocket support through an adapter layer.
- Added a UX `Connect` / `Disconnect` toggle for listening to model changes.
- Added `PlantUmlModelAdapter` to transform `VEntity` values to PlantUML
  classes and `VAttribute` values to class attributes.
- Initially displayed the generated PlantUML as plain ASCII text.
- Committed as `993bc5d Add UX model event connection and PlantUML text`.

## Render PlantUML Diagrams Visually In UX

Status: executed. Short historical task entry added after implementation.

### Goal

Replace plain PlantUML text output with visual, scrollable diagram rendering in
the UX.

### Completion Notes

- Added `@plantuml/core` to render PlantUML diagrams in the browser.
- Added `PlantUmlDiagramRendererAdapter`.
- Replaced the text area with a scrollable visual diagram viewport.
- Kept the renderer lazy-loaded so the initial UX bundle remains smaller.
- Fixed browser rendering follow-ups:
  - switched from `renderToString` to DOM rendering with completion detection;
  - loaded `viz-global.js` as a classic browser script asset before importing
    `@plantuml/core`.
- Committed as:
  - `8a9d1ab Render PlantUML diagrams visually in UX`
  - `66f4e4a Fix UX PlantUML diagram rendering`
  - `6741247 Load PlantUML Graphviz dependency as browser script`

## Simplify UX PlantUML Model Content

Status: executed. Short historical task entry added after implementation.

### Goal

Keep the visual model diagram focused on user-authored model content instead of
internal metadata.

### Completion Notes

- Removed entity `azName`, `activeSince`, and `deprecatedSince` rows from
  generated class bodies.
- Kept entity `azName` only as the internal PlantUML identifier needed for
  stable rendering.
- Rendered attribute rows with attribute `visName` instead of `azName`.
- Kept the attribute data type visible.
- Committed as `c0a33ae Simplify UX PlantUML model content`.

## Hide PlantUML Class-Specific Diagram Chrome

Status: executed. Short historical task entry added after implementation.

### Goal

Keep using PlantUML class-box layout while avoiding visual markers that imply
Vedenemo entities are implementation classes.

### Completion Notes

- Added `hide circle` to remove the class-specific `C` marker.
- Added `hide empty members` to suppress empty member compartments and their
  separator lines while keeping real attribute rows visible.
- Committed as:
  - `9c288d7 Hide class marker in UX PlantUML diagrams`
  - `d301de7 Hide empty PlantUML member compartments`
