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
