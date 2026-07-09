# Current Task

## Create ModelRoot Entity And Model Registry

Status: completed.

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

### Implementation Constraints

- Put Vedenemo-owned model types in a pure JDK module, most likely
  `vedenemo-model-api`, unless implementation analysis finds a better existing
  boundary.
- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak Javalin, JSON-library, or HTTP types into model/core/SPI modules.
- Use explicit constructor wiring.
- Do not introduce durable persistence.
- Do not implement parser, scripting, UX changes, WebSockets, or model internals
  beyond root metadata.

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
