# Vedenemo Initial Structure

This repository is an initial compiling skeleton for Project Vedenemo.

## Current scope

- Java 21 + Maven backend modules
- Pure JDK-only core rule
- SPI abstraction layer
- One initial adapter: in-memory model storage
- Minimal CLI and application composition root
- Separate Vite/TypeScript UX skeleton
- GitHub Actions workflows for backend and frontend

## Backend build

```bash
mvn clean verify
```

This compiles all Maven backend modules, runs unit/endpoint tests, and builds
the executable web API JAR.

## Run The Backend Locally

From the repository root, first build the backend:

```bash
mvn clean verify
```

Then start the web API:

```bash
java -jar vedenemo-web-api/target/vedenemo-web-api-0.1.0-SNAPSHOT.jar
```

By default the backend listens on:

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

## Quick Backend Checks

With the backend running:

```bash
curl http://127.0.0.1:8080/models/ping
```

Expected response:

```json
{"status":"ok"}
```

Create a backend session:

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

## Run VedenemoCli Locally

`VedenemoCli` connects to the running backend through HTTP. Build first:

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

Current CLI behavior:

- creates a backend session with `POST /sessions/start`
- prints the created session UUID
- shows the prompt `VedenemoCli>`
- pressing Enter on an empty line shows a new prompt
- supports `list`, `add`, `attach`, `detach`, `help`, and `exit`
- `exit` ends the backend session with `DELETE /sessions/{uuid}` and exits

See [docs/cli-reference.md](docs/cli-reference.md) for full command usage and
examples.

## Frontend build

```bash
cd vedenemo-ux
npm ci
npm run build
```

## Current milestone

The project should compile and provide a safe foundation for later agentic development.
It intentionally does not implement real Vedenemo modeling behavior yet.
