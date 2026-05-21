# Module Map

## Backend modules

```text
vedenemo-model-api
  Shared model API types. Pure JDK.

vedenemo-core-spi
  Core-facing ports and abstractions. Pure JDK except Vedenemo-owned modules.

vedenemo-core
  Command model and execution rules. Pure JDK plus Vedenemo-owned modules.

vedenemo-storage-memory
  Initial in-memory ModelStorage adapter.

vedenemo-cli
  Minimal command-line entry point.

vedenemo-app
  Composition root for wiring selected implementations together.
```

## Frontend

```text
vedenemo-ux
  Separate Vite + TypeScript skeleton.
```

## Allowed dependency direction

```text
vedenemo-core-spi -> vedenemo-model-api
vedenemo-core -> vedenemo-model-api
vedenemo-core -> vedenemo-core-spi
vedenemo-storage-memory -> vedenemo-model-api
vedenemo-storage-memory -> vedenemo-core-spi
vedenemo-cli -> vedenemo-core
vedenemo-cli -> vedenemo-storage-memory
vedenemo-app -> vedenemo-core
vedenemo-app -> vedenemo-storage-memory
```

## Forbidden dependency direction

```text
vedenemo-core -> vedenemo-storage-memory
vedenemo-core -> vedenemo-cli
vedenemo-core -> vedenemo-app
vedenemo-core -> vedenemo-ux
```
