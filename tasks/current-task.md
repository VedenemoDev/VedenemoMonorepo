# Current Task

## Taking Command concept into use and implementing the first command

Status: executed.

### Goal

Take the existing command placeholders into real use by adding the first
model-changing command: create a new `VEntity` in the currently selected model.

### Domain / Model Scope

- Extend `ModelRoot` so it directly contains and manages ordered `VEntity`
  instances.
- Add explicit operations such as `addEntity(VEntity)`,
  `removeEntity(String azName)`, `removeEntity(VEntity)`, and `entities()`.
- Preserve insertion order and enforce entity `azName` uniqueness
  case-insensitively inside one model.
- `entities()` should expose a read-only `List<VEntity>` copy in insertion
  order.
- Do not introduce a separate model aggregate/document type in this task.

### Command Architecture

- Add `CreateEntityCommand` containing target model `azName`, entity `azName`,
  and entity `visName`.
- Command execution derives entity `activeSince` from the current version of the
  target model.
- Created entities start with no attributes.
- Add `DeleteEntityCommand` as the internal undo counterpart.
- `CommandExecutor.execute(command)` should validate and apply the command to
  the selected model.
- Successful commands are recorded in session history.
- Failed commands are not recorded.
- Undo removes the latest created entity and removes the original command from
  active session command history.
- Undo reports when there is no command to undo.

### Backend / HTTP API Scope

Use command-specific endpoints:

- `POST /sessions/{uuid}/commands/create-entity`
- `POST /sessions/{uuid}/commands/undo`

Expected undo HTTP results:

- `200` when undo succeeds
- `304` when there is no command available to undo
- clear client error when session/model/command input is invalid

### CLI Scope

- `add` without an attached model keeps current model-add behavior.
- `add` with an attached model adds a new entity through the backend command
  endpoint.
- Add `undo`, which asks the backend to undo the latest executed command for
  the active session.
- Successful entity creation prints:

```text
Entity <azName> added.
```

### Tests / Verification

Add focused model, core, web API, and CLI tests where practical. At minimum,
run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model
- add entity while attached
- undo
- exit

### Documentation

After implementation, update `docs/architecture_doc.md` because this task adds
real command execution, model entity ownership, backend command endpoints, undo
behavior, and CLI command execution flow.

### Resolved Planning Decisions

- `ModelRoot` directly owns `VEntity` instances in this iteration.
- Use command-specific HTTP endpoints for this phase.
- Undo removes the original command from session command history.
- `DeleteEntityCommand` is only an internal inverse command for undo in this
  task.
- CLI keeps HTTP request DTO construction separate from backend/core command
  records.

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
- `mvn -B clean verify` passed during implementation.
