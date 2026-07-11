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
