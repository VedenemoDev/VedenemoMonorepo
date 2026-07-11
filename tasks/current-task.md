# Current Task

## Refactor ModelStorage To Use ModelRoot

Status: completed.

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

- Before execution, `ModelStorage` stored `VedenemoModel`.
- Before execution, `InMemoryModelStorage` stored `VedenemoModel`.
- `CommandExecutor` currently depends on `ModelStorage`, but does not yet use it
  for real command behavior.
- The active model API flow already uses `ModelRoot` and `ModelRegistry`.

### Completion Notes

- `ModelStorage` now stores `ModelRoot`.
- `InMemoryModelStorage` now stores `ModelRoot`.
- The obsolete `VedenemoModel` placeholder was removed because no code
  references remained after the refactor.
- Focused `InMemoryModelStorage` tests were added.
- `mvn -B clean verify` passed.
