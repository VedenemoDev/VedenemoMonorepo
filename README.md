# Vedenemo

Vedenemo is an incremental Java 21 + Maven monorepo for modeling Vedenemo
models through a pure core, HTTP backend, interactive CLI, and separate Vite
frontend skeleton.

The backend is currently process-local and in-memory. It is useful for
development, command-flow testing, and shaping the core model/API boundaries.

## Current Scope

- Java 21 + Maven multi-module backend.
- Pure JDK-only core rule for model and command logic.
- Shared model API with `ModelRoot`, `VEntity`, `VAttribute`, lifecycle version
  metadata, and initial `DataType` values.
- Core command execution for creating entities and attributes.
- Stack-based undo for the latest successful command in the current session.
- Model-level command journal used by Vedenemo Script export.
- Backend-owned `.vdos` Vedenemo Script import/export.
- Javalin HTTP/WebSocket API for models, model-change events, sessions,
  commands, undo, and script import/export.
- HTTP-backed interactive `VedenemoCli`.
- In-memory storage adapter.
- Separate Vite/TypeScript UX with model selection and PlantUML text rendering.
- GitHub Actions workflows for backend and frontend.

Not currently implemented:

- Database persistence.
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
vedenemo-app             Application composition root.
vedenemo-web-api         Javalin HTTP backend executable jar.
vedenemo-cli             HTTP-backed interactive CLI.
vedenemo-ux              Separate React + Vite frontend skeleton.
docs/                    Architecture, roadmap, and CLI reference docs.
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
GET    /models/{modelAzName}/script
POST   /models/script

POST   /sessions/start
DELETE /sessions/{uuid}
PUT    /sessions/{uuid}/selected-model
DELETE /sessions/{uuid}/selected-model
POST   /sessions/{uuid}/commands/create-entity
POST   /sessions/{uuid}/commands/create-attribute
POST   /sessions/{uuid}/commands/undo
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
undo
save [N | azName] [outputPath]
load <path>
exit
```

The CLI can create models, create entities and attributes through backend
commands, undo the latest session command, save a model to a UTF-8 `.vdos`
Vedenemo Script file, and load a `.vdos` file back through the backend.

See [docs/cli-reference.md](docs/cli-reference.md) for full command usage and
examples.

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

- The Firebase UX ping call works through the Tailscale HTTPS backend URL.

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
