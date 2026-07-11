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
end

subgraph Web["Web API Runtime"]
    WebApi["vedenemo-web-api<br/>Javalin executable jar"]
    ModelsResource["ModelsResource<br/>ping/add/list endpoints"]
end

subgraph App["Application Assembly"]
    AppRoot["vedenemo-app<br/>composition root"]
    Cli["vedenemo-cli<br/>minimal CLI entry point"]
end

subgraph Core["Core"]
    CoreModule["vedenemo-core<br/>command placeholders"]
    ModelRegistry["ModelRegistry<br/>process-local known models"]
    Spi["vedenemo-core-spi<br/>ModelStorage port"]
    ModelApi["vedenemo-model-api<br/>ModelRoot, ModelVersion"]
end

subgraph Adapters["Adapters"]
    MemoryStorage["vedenemo-storage-memory<br/>InMemoryModelStorage"]
end

ViteUX --> RuntimeConfig
ViteUX -->|fetch /models/ping| WebApi
WebApi --> ModelsResource
WebApi --> AppRoot
ModelsResource --> ModelRegistry
Cli --> CoreModule
Cli --> MemoryStorage
AppRoot --> CoreModule
AppRoot --> ModelRegistry
AppRoot --> MemoryStorage
ModelRegistry --> ModelApi
CoreModule --> Spi
CoreModule --> ModelApi
Spi --> ModelApi
MemoryStorage --> Spi
MemoryStorage --> ModelApi
```

## Backend Modules

### `vedenemo-model-api`

Shared model API module. It currently contains:

- `ModelRoot`, the first concrete model root entity
- `ModelVersion`, a normalized semantic version value

Dependencies: Java JDK only.

### `vedenemo-core-spi`

Core-facing service provider interfaces. It currently defines `ModelStorage`
with `save` and `load` operations for `ModelRoot`.

Dependencies:

- `vedenemo-model-api`
- Java JDK

### `vedenemo-core`

Core command module. It currently contains a sealed `Command` marker interface,
`NoOpCommand`, `CommandExecutor`, and `ModelRegistry`.

`ModelRegistry` is the process-local registry of currently known models. It
stores `ModelRoot` instances in insertion order and enforces case-insensitive
`azName` uniqueness while preserving the original submitted casing.

Real command behavior is not implemented yet.

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

### `vedenemo-app`

Application composition root. It currently wires `CommandExecutor` to
`InMemoryModelStorage` and creates a process-local `ModelRegistry`.

Dependencies:

- `vedenemo-core`
- `vedenemo-storage-memory`

### `vedenemo-cli`

Minimal command-line entry point. It constructs a `CommandExecutor` with
`InMemoryModelStorage` and prints a startup message.

Dependencies:

- `vedenemo-core`
- `vedenemo-storage-memory`

### `vedenemo-web-api`

Minimal HTTP runtime module. It packages an executable shaded JAR using Javalin
and exposes:

- `GET /models/ping`, which returns `{"status":"ok"}`
- `POST /models/add`, which adds a `ModelRoot` to the process-local registry
- `GET /models/list`, which returns the current process-local model registry

HTTP JSON parsing and response serialization are kept in this module. Jackson is
used here as an adapter/runtime dependency and does not leak into core or model
APIs.

Runtime configuration is read from environment variables:

- `VEDENEMO_WEB_HOST`, default `127.0.0.1`
- `VEDENEMO_WEB_PORT`, default `8080`
- `VEDENEMO_ALLOWED_ORIGINS`, default `*`

Dependencies:

- `vedenemo-app`
- `vedenemo-core`
- Javalin
- Jackson Databind
- `slf4j-simple` at runtime

## Frontend

### `vedenemo-ux`

Vite React application. It loads `/config.json` at runtime and uses
`apiBaseUrl` to call the backend ping endpoint.

Current user-facing behavior:

- renders a minimal deployment check page
- shows the configured backend URL
- includes a Ping button that calls `{apiBaseUrl}/models/ping`

The default runtime config in `vedenemo-ux/public/config.json` points to a
Tailscale HTTPS backend URL.

## Runtime Flows

### Web API Startup

```mermaid
sequenceDiagram
    participant Main as VedenemoWebApi.main
    participant Config as WebApiConfig
    participant Javalin as Javalin
    participant App as VedenemoApp
    participant Resource as ModelsResource

    Main->>Config: fromEnvironment(System.getenv())
    Main->>App: createModelRegistry()
    Main->>Javalin: create(config)
    Javalin->>Resource: register routes
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

### UX Ping

```mermaid
sequenceDiagram
    participant UX as vedenemo-ux
    participant Config as /config.json
    participant API as vedenemo-web-api

    UX->>Config: fetch runtime config
    Config-->>UX: apiBaseUrl
    UX->>API: GET /models/ping
    API-->>UX: {"status":"ok"}
```

## CI and Deployment

GitHub Actions contains separate backend and frontend CI workflows:

- backend CI runs `mvn -B clean verify`
- frontend CI runs `npm ci` and `npm run build` in `vedenemo-ux`

Backend verification includes JUnit 5 endpoint tests for the model add/list
HTTP API in `vedenemo-web-api` and focused `InMemoryModelStorage` tests for
storing and loading `ModelRoot` instances.

The UX deployment workflow builds `vedenemo-ux` and deploys it to Firebase
Hosting when required GitHub variables are present. The deployment workflow can
override `public/config.json` from the `VEDENEMO_API_BASE_URL` GitHub variable.

## Architectural Constraints Reflected In Code

- `vedenemo-core` has only Vedenemo-owned module dependencies and Java JDK APIs.
- Storage is accessed through the Vedenemo-owned `ModelStorage` SPI.
- The in-memory storage adapter depends on the SPI; core does not depend on the
  adapter.
- HTTP framework and JSON dependencies are isolated in `vedenemo-web-api`.
- Application assembly is explicit constructor wiring, currently in
  `vedenemo-app`, `vedenemo-cli`, and the web API runtime.
- The current model registry is process-local and not durable.

## Current Gaps

The current implementation does not yet contain:

- real command execution behavior
- model internals beyond `ModelRoot` metadata
- durable persistence
- parser, scripting, plugin, or visualization implementations
