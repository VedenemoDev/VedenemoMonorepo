# Vedenemo

Vedenemo is an incremental Java 21 + Maven monorepo for modeling Vedenemo
models through a pure core, HTTP backend, interactive CLI, and separate Vite
frontend skeleton.

The backend is currently process-local and in-memory. It is useful for
development, command-flow testing, and shaping the core model/API boundaries.

## General-Purpose Software

Vedenemo is general-purpose software. No representation or warranty is made
regarding its suitability for any particular application or purpose. Users are
solely responsible for evaluating whether Vedenemo is appropriate for their
intended use. See the Apache License 2.0 for the applicable warranty disclaimer
and limitation of liability.

## Current Scope

- Java 21 + Maven multi-module backend.
- Pure JDK-only core rule for model and command logic.
- Shared model API with `ModelRoot`, `VEntity`, `VAttribute`, model-level
  associations, cardinality, bidirectional relation ends, lifecycle version
  metadata, and supported `DataType` values.
- Core command execution for creating entities, attributes, directed
  ownership/reference associations, and bidirectional relations.
- Process-local runtime instance data validated against loaded model
  definitions.
- Stack-based undo for the latest successful command in the current session.
- Model-level command journal used by Vedenemo Script export.
- Backend-owned `.vdos` Vedenemo Script import/export.
- Javalin HTTP/WebSocket API for models, model-change events, sessions,
  commands, undo, script import/export, dynamic instance data, and browser
  console sessions.
- Shared Java command-flow module for terminal and browser CLI-like command
  behavior.
- HTTP-backed interactive `VedenemoCli`.
- In-memory storage adapter.
- Optional Google Cloud Storage snapshot adapter for browser console `.vdos`
  msave/mload.
- Separate Vite/TypeScript UX with model selection, PlantUML SVG rendering,
  runtime model-instance visualizations, and a full-page browser console at
  `/console`.
- GitHub Actions workflows for backend and frontend.

Not currently implemented:

- Database persistence.
- Durable model-instance data storage.
- Distributed runtime.
- REST API authentication.
- Durable event streaming or cross-process model-change notifications.
- Production-grade script grammar tooling.

## Repository Layout

```text
vedenemo-model-api       Shared model API types. Pure JDK.
vedenemo-core-spi        Core-facing SPI ports. Pure JDK plus Vedenemo modules.
vedenemo-core            Commands, sessions, undo, command journal, .vdos logic.
vedenemo-storage-memory  Initial in-memory ModelStorage adapter.
vedenemo-storage-gcs     Google Cloud Storage snapshot and dump store adapters.
vedenemo-app             Application composition root.
vedenemo-command-console Shared CLI-like command session behavior.
vedenemo-web-api         Javalin HTTP backend executable jar.
vedenemo-cli             HTTP-backed interactive CLI.
vedenemo-ux              Separate React + Vite frontend.
docs/                    Architecture, roadmap, CLI, and visualization docs.
tasks/                   Current task and historical backlog.
```

For the concrete implementation architecture, see
[docs/architecture_doc.md](docs/architecture_doc.md).

## Backend Build

From the repository root:

```bash
mvn clean verify
```

This compiles all Maven backend modules, runs tests, and builds the executable
web API JAR.

## Run The Backend Locally

Build first:

```bash
mvn clean verify
```

Start the web API:

```bash
java -jar vedenemo-web-api/target/vedenemo-web-api-0.1.0-SNAPSHOT.jar
```

Default backend URL:

```text
http://127.0.0.1:8080
```

Backend environment variables:

- `VEDENEMO_WEB_HOST`, default `127.0.0.1`
- `VEDENEMO_WEB_PORT`, default `8080`
- `VEDENEMO_ALLOWED_ORIGINS`, default `*`
- `VEDENEMO_SNAPSHOT_STORE`, set to `gcs` for browser-console cloud snapshots
- `VEDENEMO_GCS_PROJECT_ID`, required when `VEDENEMO_SNAPSHOT_STORE=gcs`
- `VEDENEMO_GCS_BUCKET`, required when `VEDENEMO_SNAPSHOT_STORE=gcs`
- `VEDENEMO_GCS_PREFIX`, required when `VEDENEMO_SNAPSHOT_STORE=gcs`
- `VEDENEMO_SNAPSHOT_SCOPE`, default `dev`

Example with an explicit local port:

```bash
VEDENEMO_WEB_HOST=127.0.0.1 VEDENEMO_WEB_PORT=18080 \
  java -jar vedenemo-web-api/target/vedenemo-web-api-0.1.0-SNAPSHOT.jar
```

## HTTP API Snapshot

With the backend running:

```bash
curl http://127.0.0.1:8080/models/ping
```

Expected response:

```json
{"status":"ok"}
```

Current API surface:

```text
GET    /models/ping
POST   /models/add
GET    /models/list
WS     /models/events
GET    /models/{modelAzName}/entities
GET    /models/{modelAzName}/entities/{entityAzName}/attributes
GET    /models/{modelAzName}/associations
GET    /models/{modelAzName}/entities/{entityAzName}/associations
GET    /models/{modelAzName}/script
POST   /models/script

GET    /data/{modelAzName}/_api
GET    /data/{modelAzName}/roots
POST   /data/{modelAzName}/roots
GET    /data/{modelAzName}/roots/{instanceRootId}
PUT    /data/{modelAzName}/roots/{instanceRootId}
GET    /data/{modelAzName}/roots/{instanceRootId}/_api
POST   /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}
GET    /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}
GET    /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_count
GET    /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/{instanceId}
POST   /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_query
POST   /data/{modelAzName}/roots/{instanceRootId}/_links/{associationAzName}
GET    /data/{modelAzName}/roots/{instanceRootId}/_links/{associationAzName}

POST   /sessions/start
DELETE /sessions/{uuid}
PUT    /sessions/{uuid}/selected-model
DELETE /sessions/{uuid}/selected-model
POST   /sessions/{uuid}/commands/create-entity
POST   /sessions/{uuid}/commands/create-attribute
POST   /sessions/{uuid}/commands/create-association
POST   /sessions/{uuid}/commands/undo

POST   /console/sessions
POST   /console/sessions/{sessionId}/commands
DELETE /console/sessions/{sessionId}
```

Session example:

```bash
curl -X POST http://127.0.0.1:8080/sessions/start
```

Expected response shape:

```json
{"sessionId":"00000000-0000-0000-0000-000000000000"}
```

End a session by replacing `<session-id>` with the returned UUID:

```bash
curl -X DELETE http://127.0.0.1:8080/sessions/<session-id>
```

Successful cleanup returns HTTP `204`.

## Dynamic Instance Data API

The `/data` API stores process-local runtime records for loaded models. Entity
instance fields are JSON object properties keyed by modeled attribute `azName`
and validated against the selected entity's `DataType`. Association links are
created through dedicated `_links` endpoints using source and target instance
ids.

Supported scalar data types include `TEXT`, `NUMERIC`, `URL`, `DATA`, `DATE`,
`TIME`, and `DATETIME`. Date and time values are stored and queried as ISO
strings; the UX formats them with the browser locale for display.

Each loaded model can have multiple process-local model-instance roots. Create
one with `POST /data/{modelAzName}/roots`; the backend returns a globally
unique `instanceRootId`. A request body may include a visual alias such as
`{"visName":"Family tree of King Charles III"}`. The alias is display metadata
only. `PUT /data/{modelAzName}/roots/{instanceRootId}` renames the alias
without changing the root id or any instance-data URLs.

Entity instance counts are available through
`GET /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_count`, which
returns a JSON object such as `{"count":2}`.

Example:

```bash
curl -X POST http://127.0.0.1:8080/data/Music/roots/<instance-root-id>/Artist \
  -H 'Content-Type: application/json' \
  -d '{"Name":"Miles Davis","Website":"https://example.com"}'
```

Relationship-aware queries use
`POST /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_query` with
one-hop relationship predicates through modeled associations.

## Run VedenemoCli Locally

`VedenemoCli` connects to the running backend through HTTP.

Build first:

```bash
mvn clean verify
```

Start the backend in one terminal:

```bash
java -jar vedenemo-web-api/target/vedenemo-web-api-0.1.0-SNAPSHOT.jar
```

Start the CLI in another terminal:

```bash
java -cp vedenemo-cli/target/classes org.vedenemo.cli.VedenemoCli
```

The CLI uses `VEDENEMO_API_BASE_URL` to find the backend. The default is
`http://127.0.0.1:8080`.

If the backend uses a custom port:

```bash
VEDENEMO_API_BASE_URL=http://127.0.0.1:18080 \
  java -cp vedenemo-cli/target/classes org.vedenemo.cli.VedenemoCli
```

Inside the CLI, `ping` checks backend connectivity through `GET /models/ping`:

```text
VedenemoCli>ping
Backend responded OK.
```

Command words are case-insensitive while parameters remain case-sensitive. In a
real terminal session, Arrow Up/Ctrl+P and Arrow Down/Ctrl+N walk command input
history for the current CLI process only. Press Esc during an interactive prompt
to cancel the current operation.

Current CLI commands:

```text
help
list
add
attach [N | azName]
detach
entities
entity [N | azName]
entity detach
attributes
attr add
associations
assoc add [ownership | reference | relation]
undo
msave [N | azName] [outputPath]
snapshots
mload <path | snapshot-number>
roots
dumps
dsave [root-id | root-number | root-name] [outputPath]
dload <path | dump-number>
exit
```

The CLI can create models, create entities and attributes through backend
commands, undo the latest session command, save a model to a UTF-8 `.vdos`
Vedenemo Script file, list `.vdos` snapshots from `.vedenemo`, and load a
`.vdos` file back through the backend. When a `.vedenemo` directory exists, CLI
`msave` defaults and relative `msave` paths use that directory; absolute
`msave` paths are used directly. The CLI can also list `.vdmp` data dumps, save
one model-instance root to a `.vdmp` file, and load a `.vdmp` file into a new
model-instance root. Use `roots` while attached to a model to list active
model-instance root numbers, visible names, model versions, and root ids before
selecting a `dsave` source. Command words are case-insensitive; parameters such
as model and entity `azName` values remain case-sensitive.

See [docs/cli-reference.md](docs/cli-reference.md) for full command usage and
examples.

## Browser Console Cloud Snapshots And Dumps

The browser console at `/console` uses the same plain command names for
snapshots and dumps, but storage is backend-managed instead of local filesystem
based:

```text
msave [snapshotName]
snapshots
mload <snapshot-key | snapshot-number>
roots
dumps
dsave [root-id | root-number | root-name] [dumpName]
dload <dump-key | dump-number>
```

Set `VEDENEMO_SNAPSHOT_STORE=gcs` and the `VEDENEMO_GCS_*` variables before
starting `vedenemo-web-api` to enable the Google Cloud Storage adapter. The
dump store can also be enabled with `VEDENEMO_DUMP_STORE=gcs`; if omitted, the
backend uses the snapshot store setting for the dump store too. The
browser never receives Google Cloud credentials; the backend uses its runtime
Application Default Credentials or service account identity.

The infrastructure scaffold for the private development bucket and service
account is under `infra/gcp/cloud-storage-snapshots`.

## Run The UX Locally

Build first:

```bash
cd vedenemo-ux
npm ci
npm run build
```

For local development, start the backend and then run the Vite dev server:

```bash
cd vedenemo-ux
npm run dev
```

The main page has top-level `Models` and `Model instances` tabs. The `Models`
tab shows model selection and the PlantUML diagram. Use the bottom-left arrow
to open the browser console as a lower split pane under the `Models` tab; drag
the pane's top border to resize it, and use the same arrow to minimize it
again. The `Model instances` tab refreshes runtime instance counts when opened
and shows a read-only tree:
`ModelVisName (Model azName)` -> one node per loaded model-instance root ->
entity groups such as `Album (460)`. Root nodes show the backend-stored visual
alias when available, otherwise a short form of the globally unique root id
with the full id as a tooltip. Use the root node's menu `Rename...` action to
update the backend-stored root alias.
Use the root node's menu `Visualize...` action to open the runtime
visualization wizard in a new browser tab. The implemented D3-backed
visualizations are `Tidy tree`, `Radial tree`, and `Tree of life`; all three
bind model entities, association traversal direction, and label templates to a
scrollable SVG tree. See
[docs/visualizations.md](docs/visualizations.md) for the full visualization
workflow and current limits.
The console output scrolls to the latest line as commands run. The full-page
browser console remains available directly at `/console`. The browser console
supports the same authoring commands as `VedenemoCli`, including `add`,
`attr add`, and `assoc add`. With the backend snapshot store configured,
browser `msave`, `snapshots`, and `mload` use cloud snapshots; terminal
`VedenemoCli` keeps using local `.vdos` files.

## Vedenemo Script Files

`.vdos` files are backend-generated UTF-8 text files. The current format has:

- `vedenemo-script 1` header
- model metadata
- authoritative command lines
- snapshot lines for final model-tree validation and readability

Example excerpt:

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

On import, command lines are replayed and the resulting model is validated
against the snapshot. Imported commands become baseline model state and are not
added to the current session undo stack.

Development-time model-instance dumps use `.vdmp` JSON files. The format is
documented in [docs/model-instance-dump-format.md](docs/model-instance-dump-format.md).

## Frontend Build

```bash
cd vedenemo-ux
npm ci
npm run build
```

## Current Tailscale / Firebase UX Connectivity

Current tested development connectivity:

- WSL backend service runs the web API locally on port `8080`.
- Tailscale Serve exposes that local backend as:

```text
https://vedenemo-wsl.tail64b6af.ts.net
```

- The deployed Firebase UX is:

```text
https://vedenemo-ux-prod.web.app/
```

- Backend connectivity can be checked with `curl /models/ping`, the terminal
  CLI `ping` command, or the `/console` virtual CLI `ping` command.

The browser that opens the Firebase UX must have access to the same Tailscale
tailnet. Firebase Hosting itself is not part of the tailnet; the browser reaches
the tailnet backend directly.

For this setup, keep the frontend runtime API base URL as:

```json
{
  "apiBaseUrl": "https://vedenemo-wsl.tail64b6af.ts.net"
}
```

The backend can remain bound locally to `127.0.0.1:8080` when Tailscale Serve is
proxying HTTPS traffic to it.
