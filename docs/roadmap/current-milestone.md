# Current Milestone

## Goal

Keep Vedenemo in a coherent early product-development state: a compiling
multi-module backend with strict core boundaries, a usable HTTP-backed CLI, and
enough import/export capability to preserve and replay model work between
process-local runs.

This milestone supersedes the original first skeleton milestone. The project is
no longer only a compiling module structure: it now includes process-local model
editing through HTTP and CLI, command undo, and `.vdos` Vedenemo Script
save/load support.

## Current State

The repository currently provides:

- Java 21 Maven multi-module backend.
- Pure JDK-only `vedenemo-core` and `vedenemo-model-api` rules.
- `ModelStorage` SPI and in-memory storage adapter.
- `ModelRoot`, `VEntity`, `VAttribute`, `ModelVersion`, lifecycle version
  metadata, and initial `DataType` values.
- Process-local `ModelRegistry`.
- Session lifecycle with selected model context.
- Command execution for creating entities and attributes.
- Stack-based undo for the latest executed command in the active session.
- Model-level command journal for model-targeting commands.
- Backend-owned `.vdos` Vedenemo Script export/import.
- Javalin web API for model listing, model details, model-change WebSocket
  events, sessions, commands, undo, and `.vdos` script import/export.
- HTTP-backed interactive `VedenemoCli`.
- Separate Vite/TypeScript frontend with model selection, model-change
  connection, and PlantUML SVG rendering.
- Backend and frontend GitHub Actions workflows.

## Done Criteria For This Milestone

- `mvn clean verify` succeeds from the repository root.
- `cd vedenemo-ux && npm ci && npm run build` succeeds when frontend
  dependencies are installed.
- `vedenemo-core` has no direct third-party dependencies.
- Core-owned model and command rules stay independent of web, CLI, storage, and
  frontend modules.
- The web API can run locally as an executable JAR.
- The web API can emit process-local model-change events over WebSocket.
- `VedenemoCli` can create a session, add/list/attach models, create entities,
  create attributes, undo the latest session command, save `.vdos` files, and
  load `.vdos` files.
- `.vdos` export includes model metadata, command lines, and final snapshot
  lifecycle metadata.
- `.vdos` import replays authoritative command lines and validates the result
  against the snapshot.
- Documentation reflects the current implementation:
  - `README.md`
  - `docs/architecture_doc.md`
  - `docs/cli-reference.md`
  - `tasks/current-task.md`
  - `tasks/backlog.md`

## Current Constraints

- Backend state is process-local and in-memory.
- `.vdos` is the current local interchange/preservation format.
- Loaded `.vdos` commands become baseline model state and are not added to the
  active session undo stack.
- Duplicate `.vdos` model imports reject first; CLI can retry with a new model
  `azName`.
- User-visible edit/delete commands are still limited; delete commands currently
  exist as undo counterparts.
- No database persistence adapter is implemented yet.
- No authentication/authorization is implemented for the local web API.
- WebSocket model-change events are process-local runtime notifications, not
  durable event storage.
- No production parser-generator based `.vdos` grammar tooling is implemented.

## Near-Term Direction

The next work should continue strengthening the local model authoring loop while
preserving the established boundaries:

- keep business rules in `vedenemo-core` or `vedenemo-model-api`;
- keep HTTP DTOs and JSON handling in `vedenemo-web-api`;
- keep CLI behavior as a thin HTTP/file-I/O client;
- add persistence adapters behind Vedenemo-owned interfaces when persistence is
  introduced;
- plan association modeling in phases, starting with cardinality and directed
  reference attributes for `owns`/`references` before adding true
  bidirectional `relation` support;
- keep relationship semantics explicit in pure model/core code while keeping
  HTTP DTOs, CLI prompts, UX rendering, and `.vdos` text handling in their
  existing module boundaries;
- keep current architecture documentation synchronized when module boundaries,
  command flows, API endpoints, or script behavior changes.
