# Current Task

## Create Session concept, bind it to CommandExecutor, and start new session via command-line interface

Status: completed.

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

The update should document only the concrete implementation that exists after
the task is complete:

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
