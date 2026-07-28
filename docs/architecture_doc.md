# Vedenemo Current Architecture

This document describes the current concrete implementation in this repository.
It is intentionally separate from architecture definition, roadmap, and planning
documents, which may describe rules or intended future direction.

## Overview

Vedenemo is currently a monorepo with a Java 21 Maven backend, a separate Vite
React frontend, GitHub Actions CI, and Firebase Hosting deployment for the UX.
The backend is split into small Maven modules with a pure core and adapter
modules around it.

```mermaid
flowchart TB

subgraph UX["Frontend"]
    ViteUX["vedenemo-ux<br/>React + Vite"]
    RuntimeConfig["public/config.json<br/>runtime API base URL"]
    UXModelEvents["ModelChangeEventAdapter<br/>browser WebSocket listener"]
    UXPlantUml["PlantUmlModelAdapter<br/>model-to-PlantUML source"]
    UXPlantUmlRenderer["PlantUmlDiagramRendererAdapter<br/>lazy PlantUML SVG renderer"]
    UXConsole["/console<br/>browser virtual CLI"]
end

subgraph Web["Web API Runtime"]
    WebApi["vedenemo-web-api<br/>Javalin executable jar"]
    ModelsResource["ModelsResource<br/>ping/add/list/read model endpoints"]
    SessionResource["SessionResource<br/>session lifecycle, selected model, and command endpoints"]
    ConsoleResource["ConsoleResource<br/>browser console-session endpoints"]
    ConsoleRegistry["WebConsoleSessionRegistry<br/>browser session wrapper"]
    ModelEvents["ModelChangeBroadcaster<br/>/models/events WebSocket"]
end

subgraph App["Application Assembly"]
    AppRoot["vedenemo-app<br/>composition root"]
    Cli["vedenemo-cli<br/>HTTP-backed interactive CLI"]
    CommandConsole["vedenemo-command-console<br/>shared CLI-like command flow"]
end

subgraph Core["Core"]
    CoreModule["vedenemo-core<br/>CommandExecutor, commands, undo"]
    ScriptService["VedenemoScriptService<br/>.vdos import/export"]
    CommandJournal["ModelCommandJournal<br/>model-level command history"]
    SessionManager["SessionManager<br/>process-local active sessions"]
    Session["Session<br/>UUID, selected model, command history"]
    ModelRegistry["ModelRegistry<br/>process-local known models"]
    Spi["vedenemo-core-spi<br/>ModelStorage and SnapshotStore ports"]
    ModelApi["vedenemo-model-api<br/>ModelRoot, entities, attributes, associations"]
end

subgraph Adapters["Adapters"]
    MemoryStorage["vedenemo-storage-memory<br/>InMemoryModelStorage"]
    GcsStorage["vedenemo-storage-gcs<br/>GcsSnapshotStore"]
end

ViteUX --> RuntimeConfig
ViteUX -->|fetch model data| UXPlantUml
ViteUX -->|connect/disconnect| UXModelEvents
ViteUX -->|lazy render diagram| UXPlantUmlRenderer
ViteUX --> UXConsole
UXPlantUml -->|GET model/entity/attribute APIs| WebApi
UXModelEvents -->|WebSocket /models/events| WebApi
UXConsole -->|HTTP console commands| WebApi
Cli -->|HTTP model and session APIs| WebApi
Cli --> CommandConsole
WebApi --> ModelsResource
WebApi --> SessionResource
WebApi --> ConsoleResource
WebApi --> ModelEvents
WebApi --> AppRoot
WebApi --> GcsStorage
ConsoleResource --> ConsoleRegistry
ConsoleRegistry --> CommandConsole
ConsoleRegistry --> SessionManager
ConsoleRegistry --> ModelRegistry
ModelsResource --> ModelRegistry
ModelsResource --> ScriptService
ModelsResource --> ModelEvents
SessionResource --> SessionManager
SessionResource --> ModelRegistry
SessionResource --> ModelEvents
AppRoot --> CoreModule
AppRoot --> ModelRegistry
AppRoot --> SessionManager
AppRoot --> MemoryStorage
SessionManager --> Session
SessionManager --> CoreModule
SessionManager --> CommandJournal
CoreModule --> CommandJournal
ScriptService --> ModelRegistry
ScriptService --> CommandJournal
ScriptService --> ModelApi
ModelRegistry --> ModelApi
CoreModule --> Spi
CoreModule --> ModelApi
CoreModule --> Session
Spi --> ModelApi
MemoryStorage --> Spi
MemoryStorage --> ModelApi
GcsStorage --> Spi
```

## Backend Modules

### `vedenemo-model-api`

Shared model API module. It currently contains:

- `ModelRoot`, the first concrete model root entity, which owns ordered
  `VEntity` instances and ordered model-level associations
- `ModelVersion`, a normalized semantic version value
- `Versionable`, an abstract base class for model elements with lifecycle
  version metadata
- `Cardinality`, a pure value object for association multiplicity text such as
  `1`, `0..1`, `0..*`, `1..*`, and bounded ranges
- `DataType`, the initial enum of supported attribute data types:
  `TEXT`, `NUMERIC`, `URL`, and `DATA`
- `VAttribute`, a model attribute with `azName`, `visName`, `DataType`, and
  lifecycle version metadata
- `VEntity`, a model entity with `azName`, `visName`, lifecycle version
  metadata, and an ordered attribute collection
- `Association`, a sealed interface for first-class model-level associations
- `OwnershipAssociation` and `ReferenceAssociation`, directed associations
  with `azName`, `visName`, source entity `azName`, target entity `azName`,
  `Cardinality`, and lifecycle version metadata
- `RelationAssociation` and `RelationEnd`, which model a bidirectional
  relation as one association identity with two named ends; each end has an
  entity `azName`, role name, and `Cardinality`

`ModelRoot`, `VEntity`, and `VAttribute` share the same `azName` rule: the name
must start with an ASCII letter and then contain only ASCII letters, ASCII
digits, and underscores. Original casing is preserved. Case-insensitive
uniqueness keys are used where uniqueness is enforced.

`VEntity` preserves attribute insertion order and enforces attribute `azName`
uniqueness case-insensitively. It exposes attributes as a read-only snapshot
list. Attributes can be removed by `azName` or by `VAttribute` instance while a
model is under construction.

`ModelRoot` preserves entity and association insertion order. It enforces entity
`azName` uniqueness and association `azName` uniqueness case-insensitively in
separate model-level namespaces. Association creation validates that source and
target entities already exist. Relations validate both named ends through the
same model-level association collection. Entities and associations are exposed
as read-only snapshot lists and can be removed while a model is under
construction or while undo applies an inverse command.

`Versionable` requires `activeSince`. `deprecatedSince` is optional, but when it
is present it must be strictly later than `activeSince`.

Dependencies: Java JDK only.

### `vedenemo-core-spi`

Core-facing service provider interfaces. It currently defines `ModelStorage`
with `save` and `load` operations for `ModelRoot`, and `SnapshotStore` for
Vedenemo `.vdos` snapshot artifacts. Snapshot records carry a backend-owned key,
model metadata, command count, and saved timestamp.

Dependencies:

- `vedenemo-model-api`
- Java JDK

### `vedenemo-core`

Core command module. It currently contains a sealed `Command` marker interface,
`NoOpCommand`, `CreateEntityCommand`, `CreateAttributeCommand`,
`CreateAssociationCommand`, internal `DeleteEntityCommand`,
`DeleteAttributeCommand`, and `DeleteAssociationCommand` counterparts,
`UndoResult`,
`ModelCommandJournal`, `VedenemoScriptService`, `CommandExecutor`, `Session`,
`SessionManager`, and `ModelRegistry`.

`Session` represents a process-local user work session. It has a UUID, an
optional selected model reference stored as `ModelRoot.azName`, execution-order
command history, a reverse-order command history snapshot, and a latest-command
removal operation used by undo.

`SessionManager` creates sessions and keeps active session-bound
`CommandExecutor` instances in memory. Ending a session removes the active
executor and session from the manager. Each session manager owns a
`ModelCommandJournal` shared by all command executors it creates.

`ModelCommandJournal` is a process-local model-level command history. It stores
model-targeting commands by model `azName` so export is not dependent on one
active CLI session's undo stack. `NoOpCommand` is session-only and is not stored
in the model journal.

`CommandExecutor` is bound to exactly one active `Session` and the process-local
`ModelRegistry`. It can execute `CreateEntityCommand` against the selected
model, `CreateAttributeCommand` against an entity in the selected model, and
`CreateAssociationCommand` against the selected model. `CreateAssociationCommand`
creates directed ownership/reference associations or bidirectional relations
depending on its `AssociationKind` and relation end fields. Successful commands
are recorded in the session and in the model-level command journal. Failed
commands are not recorded. Undo is stack-based and only applies to the latest
successful command: create-entity is undone through the internal
`DeleteEntityCommand` inverse, create-attribute is undone through the internal
`DeleteAttributeCommand` inverse, and create-association is undone through the
internal `DeleteAssociationCommand` inverse. Undo removes the original command
from active session command history and from the model-level command journal,
then returns a core-owned `UndoResult` containing a stable command identifier
such as `create-entity`, `create-attribute`, or `create-association` plus the
target names needed by clients.

`VedenemoScriptService` owns the current `.vdos` Vedenemo Script import/export
format. The format is UTF-8 text with:

- `vedenemo-script 1` header
- one model metadata line
- a `commands` section using stable command slugs such as `create-entity`,
  `create-attribute`, and `create-association`
- a `snapshot` section containing the final entity/attribute tree, model-level
  associations, and lifecycle version metadata

Association command and snapshot lines include common fields for association
identity, kind, endpoints, cardinality, and lifecycle metadata. Relation lines
add source and target role/cardinality fields so both named ends are preserved
as one association definition. Directed ownership/reference lines omit those
relation-only fields.

Commands are authoritative during import. The service replays the command
section into a new `ModelRoot` and validates the result against the snapshot.
Imported commands are stored as model-level baseline command history and are not
added to any active session undo stack.

`ModelRegistry` is the process-local registry of currently known models. It
stores `ModelRoot` instances in insertion order and enforces case-insensitive
`azName` uniqueness while preserving the original submitted casing.

User-visible attribute deletion as a standalone edit operation is not
implemented yet; `DeleteAttributeCommand` exists only as the internal inverse
for undoing attribute creation.

Dependencies:

- `vedenemo-model-api`
- `vedenemo-core-spi`
- Java JDK

### `vedenemo-storage-memory`

Initial in-memory storage adapter. `InMemoryModelStorage` implements
`ModelStorage` with a `HashMap` of `ModelRoot` instances.

Dependencies:

- `vedenemo-model-api`
- `vedenemo-core-spi`
- Java JDK

### `vedenemo-storage-gcs`

Google Cloud Storage adapter for Vedenemo `.vdos` snapshots. `GcsSnapshotStore`
implements `SnapshotStore` by storing UTF-8 `.vdos` content as bucket objects
under the configured object prefix. Snapshot object metadata stores model
`azName`, visible name, version, command count, saved timestamp, scope, and
format version.

The adapter depends on the Google Cloud Storage client library. The dependency
is isolated to this module and the web API composition layer; core and model
modules remain pure JDK plus Vedenemo-owned dependencies.

Dependencies:

- `vedenemo-core-spi`
- Google Cloud Storage client

### `vedenemo-app`

Application composition root. It currently wires `CommandExecutor` to
`InMemoryModelStorage`, creates process-local `SessionManager` instances, and
creates a process-local `ModelRegistry`.

Dependencies:

- `vedenemo-core`
- `vedenemo-storage-memory`

### `vedenemo-command-console`

Shared CLI-like command-flow module. It contains the session-oriented command
dispatcher, multi-step prompt-flow state, prompt rendering, command result
type, terminal/web capability flags, and Vedenemo-owned client DTO/interfaces
used by command frontends.

The module is intentionally UI-neutral:

- it does not read terminal stdin or write terminal stdout;
- it does not render browser UI;
- it does not perform local filesystem access;
- it routes `save`, `snapshots`, and `load` through capability-specific client
  operations: terminal CLI keeps local filesystem behavior, while browser
  console sessions can use backend-managed cloud snapshots.

Terminal CLI adapters and web API in-process adapters implement the shared
client interfaces so both entry points use the same non-file command behavior.

Dependencies:

- Java JDK

### `vedenemo-cli`

Minimal command-line entry point. It reads the backend base URL from
`VEDENEMO_API_BASE_URL`, defaulting to `http://127.0.0.1:8080`, creates a
backend session through `POST /sessions/start`, and enters an interactive prompt
loop. Common CLI-like command DTOs and command-session behavior are supplied by
`vedenemo-command-console`; terminal input/output and local `.vdos` file access
remain in `vedenemo-cli`.

Current CLI behavior:

- prints the created session UUID
- prompts with `VedenemoCli>`
- prompts with `VedenemoCli[azName]>` when a model is attached
- prompts with `VedenemoCli[modelAzName/entityAzName]>` when a model is
  attached and an entity is selected
- treats an empty line as an empty echo plus a new prompt
- treats command words case-insensitively while preserving case-sensitive
  parameter text
- keeps terminal command input history for the current CLI process only when
  attached to a real TTY, navigable with Arrow Up, Arrow Down, Ctrl+P, and
  Ctrl+N
- cancels the current terminal interactive prompt flow when Esc is pressed
- supports `help`
- lists current backend models with `list`
- adds a new backend model with `add`, using version `1.0.0`
- when a model is attached, reuses `add` to create a new entity in that model
  through the backend command API
- lists entities in the attached model with `entities`
- selects an entity with `entity [N | azName]`
- clears only selected entity context with `entity detach`
- lists attributes in the selected entity with `attributes`, including data
  type and lifecycle version fields
- creates attributes in the selected entity with `attr add`
- lists associations with `associations`; when an entity is selected the list is
  scoped to associations touching that entity, otherwise it is model-scoped
- creates directed ownership/reference associations and bidirectional relations
  with `assoc add`, `assoc add ownership`, `assoc add reference`, or
  `assoc add relation`
- attaches the session to a model by latest list number or `azName`
- detaches the session from the current selected model
- supports `undo` for the latest backend command and prints operation-specific
  undo output
- supports `save [N | azName] [outputPath]`, which exports backend-generated
  `.vdos` text for a model and writes it as UTF-8 to a local file; when a
  local `.vedenemo` directory exists, default and relative save paths use that
  directory, while absolute save paths are used directly
- supports `snapshots`, which lists `.vdos` files from a local `.vedenemo`
  directory for numbered terminal loading
- supports `load <path | snapshot-number>`, which reads a UTF-8 `.vdos` file,
  prefers `.vedenemo` for bare relative names when a matching file exists,
  imports the file through the backend, and attaches to the loaded model
- supports `exit`
- calls `DELETE /sessions/{uuid}` during normal exit and through a best-effort
  shutdown hook

Dependencies:

- `vedenemo-command-console`
- Java JDK

### `vedenemo-web-api`

Minimal HTTP runtime module. It packages an executable shaded JAR using Javalin
and exposes:

- `GET /models/ping`, which returns `{"status":"ok"}`
- `POST /models/add`, which adds a `ModelRoot` to the process-local registry
- `GET /models/list`, which returns the current process-local model registry
- `GET /models/{modelAzName}/entities`, which lists entities in insertion order
  for one model
- `GET /models/{modelAzName}/entities/{entityAzName}/attributes`, which lists
  attributes in insertion order for one entity, including `DataType` and
  lifecycle version fields
- `GET /models/{modelAzName}/associations`, which lists model-level
  associations in insertion order
- `GET /models/{modelAzName}/entities/{entityAzName}/associations`, which lists
  associations touching one entity as a single ordered list with explicit source
  and target fields
- `GET /models/{modelAzName}/script`, which exports one model as UTF-8
  `text/plain` `.vdos` content using the model-level command journal and
  current model snapshot
- `POST /models/script`, which imports UTF-8 `.vdos` content as baseline model
  state and returns the imported model `azName` and command count
- `GET /models/events` as a WebSocket endpoint that emits model change events
  to connected clients
- `POST /sessions/start`, which creates a backend session and returns its UUID
- `DELETE /sessions/{uuid}`, which ends/removes a backend session
- `PUT /sessions/{uuid}/selected-model`, which selects an existing model for a
  backend session
- `DELETE /sessions/{uuid}/selected-model`, which clears the selected model for
  a backend session
- `POST /sessions/{uuid}/commands/create-entity`, which creates a `VEntity` in
  the session's selected model through `CommandExecutor`
- `POST /sessions/{uuid}/commands/create-attribute`, which creates a
  `VAttribute` in an entity in the session's selected model through
  `CommandExecutor`
- `POST /sessions/{uuid}/commands/create-association`, which creates a directed
  ownership/reference association or bidirectional relation in the session's
  selected model through `CommandExecutor`
- `POST /sessions/{uuid}/commands/undo`, which undoes the latest command for
  the active backend session and returns the undone command slug plus target
  details, or returns `304` when nothing can be undone
- `POST /console/sessions`, which creates a browser-facing console session
  wrapper and an internal backend edit session
- `POST /console/sessions/{sessionId}/commands`, which executes one CLI-like
  command line through the shared console module and returns output lines,
  status, prompt, and attached model context
- `DELETE /console/sessions/{sessionId}`, which ends the browser-facing console
  session and its owned backend edit session

HTTP JSON parsing and response serialization are kept in this module. Jackson is
used here as an adapter/runtime dependency and does not leak into core or model
APIs.

`WebConsoleSessionRegistry` owns browser-facing console session ids. Each
console session wraps a `vedenemo-command-console` `ConsoleSession`, which in
turn owns or links one backend edit session UUID. This keeps web-console browser
state separate from the generic backend session API while reusing existing
model-editing behavior under the hood.

Browser console `save`, `snapshots`, and `load` commands are handled by the web
API in-process console adapter. When `VEDENEMO_SNAPSHOT_STORE=gcs` is configured,
the adapter exports the attached model through `VedenemoScriptService`, writes
it through `SnapshotStore`, lists snapshot descriptors from the configured
scope, and imports loaded snapshot content through the same `.vdos` import
service used by normal script uploads. If a loaded snapshot conflicts with an
existing model `azName`, the browser console prompts for a replacement import
`azName`. Without a configured snapshot store, these commands return a clear
cloud snapshot store configuration error.

`ModelChangeBroadcaster` is a web-runtime adapter for browser model-change
listening. It owns the Javalin WebSocket endpoint and broadcasts UTF-8 JSON
messages such as:

```json
{"type":"model-changed","modelAzName":"Example_Model","occurredAt":"2026-07-21T15:00:00Z"}
```

Model changes are broadcast after successful model creation, `.vdos` import,
entity creation, attribute creation, association creation, and undo operations.
The event stream is a process-local runtime notification channel; it is not
durable persistence.

Runtime configuration is read from environment variables:

- `VEDENEMO_WEB_HOST`, default `127.0.0.1`
- `VEDENEMO_WEB_PORT`, default `8080`
- `VEDENEMO_ALLOWED_ORIGINS`, default `*`
- `VEDENEMO_SNAPSHOT_STORE`; set to `gcs` to enable the Google Cloud snapshot
  adapter
- `VEDENEMO_GCS_PROJECT_ID`, required when `VEDENEMO_SNAPSHOT_STORE=gcs`
- `VEDENEMO_GCS_BUCKET`, required when `VEDENEMO_SNAPSHOT_STORE=gcs`
- `VEDENEMO_GCS_PREFIX`, required when `VEDENEMO_SNAPSHOT_STORE=gcs`
- `VEDENEMO_SNAPSHOT_SCOPE`, default `dev`

Dependencies:

- `vedenemo-app`
- `vedenemo-command-console`
- `vedenemo-core`
- `vedenemo-core-spi`
- `vedenemo-storage-gcs`
- Javalin
- Jackson Databind
- `slf4j-simple` at runtime

## Frontend

### `vedenemo-ux`

Vite React application. It loads `/config.json` at runtime and uses
`apiBaseUrl` to call backend HTTP endpoints and connect to the backend model
event WebSocket.

Current user-facing behavior:

- fetches available models from `{apiBaseUrl}/models/list` at page load
- shows the number of available models after model list refresh
- provides a Refresh model list button
- lets the user select a model from a dropdown
- provides a Connect/Disconnect toggle for `{apiBaseUrl}/models/events`
- refreshes the selected model view when backend model-change events arrive
- renders the selected model as an automatically laid out PlantUML SVG class
  diagram in a scrollable viewport
- shows transient diagram rendering status below the diagram viewport
- exposes `/console` as a separate full-page virtual CLI that starts a backend
  console session, appends command output to a terminal-like history, and runs
  one command or prompt answer at a time through
  `{apiBaseUrl}/console/sessions`
- passes the connected model `azName` into `/console` only when the main UX has
  an active model event connection
- keeps browser console command input history for the current console page
  session only, navigable with Arrow Up, Arrow Down, Ctrl+P, and Ctrl+N
- keeps browser console focus on the command input and lets Esc clear/cancel
  the current command entry or pending backend prompt flow
- supports browser console `save`, `snapshots`, and `load` through the backend
  when the backend snapshot store is configured; the browser never receives
  Google Cloud credentials

The default runtime config in `vedenemo-ux/public/config.json` points to a
Tailscale HTTPS backend URL.

Frontend adapter responsibilities:

- `ModelChangeEventAdapter` owns browser WebSocket connection lifecycle and
  translates backend model-change messages into callbacks.
- `PlantUmlModelAdapter` reads the selected model through existing HTTP model,
  entity, attribute, and association endpoints. It transforms `VEntity`
  instances to PlantUML classes, `VAttribute` instances to class attributes,
  ownership associations to composition-style edges, reference associations to
  aggregation-style edges, and relations to solid undirected edges with role and
  cardinality labels at both ends.
- `PlantUmlDiagramRendererAdapter` lazy-loads `@plantuml/core` and renders the
  generated PlantUML source to SVG in the browser. The heavy renderer chunk is
  loaded only when a diagram is rendered.

## Runtime Flows

### Web API Startup

```mermaid
sequenceDiagram
    participant Main as VedenemoWebApi.main
    participant Config as WebApiConfig
    participant Javalin as Javalin
    participant App as VedenemoApp
    participant Models as ModelsResource
    participant Sessions as SessionResource
    participant Console as ConsoleResource

    Main->>Config: fromEnvironment(System.getenv())
    Main->>App: createModelRegistry()
    Main->>App: createSessionManager()
    Main->>Javalin: create(config)
    Javalin->>Models: register model routes
    Javalin->>Sessions: register session routes
    Javalin->>Console: register console session routes
    Main->>Javalin: start(host, port)
```

### Add And List Models

```mermaid
sequenceDiagram
    participant Client as HTTP client
    participant Resource as ModelsResource
    participant Registry as ModelRegistry
    participant Model as ModelRoot

    Client->>Resource: POST /models/add
    Resource->>Model: create(azName, visName, version)
    Resource->>Registry: add(modelRoot)
    Registry-->>Resource: created modelRoot
    Resource-->>Client: 201 created model root

    Client->>Resource: GET /models/list
    Resource->>Registry: list()
    Registry-->>Resource: model roots in insertion order
    Resource-->>Client: 200 model root list
```

### CLI Session Lifecycle

```mermaid
sequenceDiagram
    participant CLI as vedenemo-cli
    participant API as vedenemo-web-api
    participant Resource as SessionResource
    participant Manager as SessionManager
    participant Session as Session
    participant Executor as CommandExecutor

    CLI->>API: POST /sessions/start
    API->>Resource: route request
    Resource->>Manager: startSession()
    Manager->>Session: create UUID session
    Manager->>Executor: create session-bound executor
    Resource-->>CLI: 201 {"sessionId":"..."}
    CLI->>CLI: prompt VedenemoCli>
    CLI->>API: DELETE /sessions/{uuid}
    API->>Resource: route request
    Resource->>Manager: endSession(uuid)
    Manager-->>Resource: removed session
    Resource-->>CLI: 204
```

### CLI Model Management

```mermaid
sequenceDiagram
    participant CLI as vedenemo-cli
    participant API as vedenemo-web-api
    participant Models as ModelsResource
    participant Sessions as SessionResource
    participant Registry as ModelRegistry
    participant Session as Session

    CLI->>API: GET /models/list
    API->>Models: route request
    Models->>Registry: list()
    Registry-->>CLI: model root list

    CLI->>API: POST /models/add
    API->>Models: route request
    Models->>Registry: add(ModelRoot)
    Registry-->>CLI: created model root

    CLI->>API: PUT /sessions/{uuid}/selected-model
    API->>Sessions: route request
    Sessions->>Registry: contains(azName)
    Sessions->>Session: selectModel(azName)
    Sessions-->>CLI: 204

    CLI->>API: DELETE /sessions/{uuid}/selected-model
    API->>Sessions: route request
    Sessions->>Session: clearSelectedModel()
    Sessions-->>CLI: 204
```

### CLI Entity, Attribute, And Association Commands With Undo

```mermaid
sequenceDiagram
    participant CLI as vedenemo-cli
    participant API as vedenemo-web-api
    participant Sessions as SessionResource
    participant Manager as SessionManager
    participant Executor as CommandExecutor
    participant Registry as ModelRegistry
    participant Model as ModelRoot
    participant Entity as VEntity

    CLI->>API: POST /sessions/{uuid}/commands/create-entity
    API->>Sessions: route request
    Sessions->>Manager: findExecutor(uuid)
    Sessions->>Executor: execute(CreateEntityCommand)
    Executor->>Registry: find(selected model azName)
    Executor->>Model: addEntity(VEntity)
    Executor->>Executor: record command in Session
    Sessions-->>CLI: 200 entity response

    CLI->>API: GET /models/{modelAzName}/entities
    API-->>CLI: entity list
    CLI->>CLI: select entity context

    CLI->>API: POST /sessions/{uuid}/commands/create-attribute
    API->>Sessions: route request
    Sessions->>Manager: findExecutor(uuid)
    Sessions->>Executor: execute(CreateAttributeCommand)
    Executor->>Registry: find(selected model azName)
    Executor->>Model: find entity
    Executor->>Entity: addAttribute(VAttribute)
    Executor->>Executor: record command in Session
    Sessions-->>CLI: 200 attribute response

    CLI->>API: POST /sessions/{uuid}/commands/create-association
    API->>Sessions: route request
    Sessions->>Manager: findExecutor(uuid)
    Sessions->>Executor: execute(CreateAssociationCommand)
    Executor->>Registry: find(selected model azName)
    Executor->>Model: addAssociation(Association)
    Executor->>Executor: record command in Session
    Sessions-->>CLI: 200 association response

    CLI->>API: POST /sessions/{uuid}/commands/undo
    API->>Sessions: route request
    Sessions->>Manager: findExecutor(uuid)
    Sessions->>Executor: undoLatest()
    Executor->>Model: remove latest command target
    Executor->>Executor: remove original command from Session
    Sessions-->>CLI: 200 undo response with command slug and target details
```

### UX Model Selection And PlantUML Rendering

```mermaid
sequenceDiagram
    participant UX as vedenemo-ux
    participant Config as /config.json
    participant API as vedenemo-web-api
    participant Events as /models/events WebSocket

    UX->>Config: fetch runtime config
    Config-->>UX: apiBaseUrl
    UX->>API: GET /models/list
    API-->>UX: model summaries
    UX->>Events: connect
    Events-->>UX: connected
    UX->>API: GET /models/{modelAzName}/entities
    UX->>API: GET /models/{modelAzName}/entities/{entityAzName}/attributes
    UX->>API: GET /models/{modelAzName}/associations
    UX->>UX: lazy-load PlantUML renderer and render SVG
    Events-->>UX: model-changed
    UX->>API: refresh selected model data
```

### Web Console Command Execution

```mermaid
sequenceDiagram
    participant UX as vedenemo-ux /console
    participant API as vedenemo-web-api
    participant Resource as ConsoleResource
    participant Registry as WebConsoleSessionRegistry
    participant Console as ConsoleSession
    participant Manager as SessionManager

    UX->>API: POST /console/sessions
    API->>Resource: route request
    Resource->>Registry: startSession(connectedModelAzName?)
    Registry->>Manager: startSession()
    Registry->>Console: create shared console session
    Resource-->>UX: 201 console session id, prompt, attached model

    UX->>API: POST /console/sessions/{sessionId}/commands
    API->>Resource: route request
    Resource->>Registry: find browser console session
    Resource->>Console: execute(command line)
    Console-->>Resource: status, output lines, prompt
    Resource-->>UX: 200 command response

    UX->>API: DELETE /console/sessions/{sessionId}
    API->>Resource: route request
    Resource->>Registry: endSession(sessionId)
    Registry->>Manager: endSession(backend session UUID)
    Resource-->>UX: 204
```

## CI and Deployment

GitHub Actions contains separate backend and frontend CI workflows:

- backend CI runs `mvn -B clean verify`
- frontend CI runs `npm ci` and `npm run build` in `vedenemo-ux`

Backend verification includes focused model API tests for `ModelRoot`,
`VAttribute`, `VEntity`, and lifecycle validation, focused core tests for
`Session`, `SessionManager`, create-entity/create-attribute command execution,
and undo behavior, focused CLI tests for backend URL configuration, non-hanging
prompt behavior, model commands, entity context, attribute command creation, and
undo output, JUnit 5 endpoint tests for the model add/list/entity/attribute
read APIs, session lifecycle, selected-model, create-entity, create-attribute,
and undo HTTP APIs in `vedenemo-web-api`, and focused `InMemoryModelStorage`
tests for storing and loading `ModelRoot` instances.

The UX deployment workflow builds `vedenemo-ux` and deploys it to Firebase
Hosting when required GitHub variables are present. The deployment workflow can
override `public/config.json` from the `VEDENEMO_API_BASE_URL` GitHub variable.

## Architectural Constraints Reflected In Code

- `vedenemo-core` has only Vedenemo-owned module dependencies and Java JDK APIs.
- Storage is accessed through the Vedenemo-owned `ModelStorage` SPI.
- The in-memory storage adapter depends on the SPI; core does not depend on the
  adapter.
- HTTP framework and JSON dependencies are isolated in `vedenemo-web-api`.
- The CLI talks to the backend through JDK HTTP client APIs and does not import
  Javalin, Jackson, or Vedenemo core types.
- Shared CLI-like command behavior lives in `vedenemo-command-console`; terminal
  and browser entry points provide their own transport/UI adapters.
- Application assembly is explicit constructor wiring, currently in
  `vedenemo-app`, `vedenemo-cli`, and the web API runtime.
- The current model registry and session manager are process-local and not
  durable.

## Current Gaps

The current implementation does not yet contain:

- user-visible deletion/edit commands for entities or attributes
- generic command envelope or command replay persistence
- durable persistence
- parser generator based `.vdos` grammar tooling
