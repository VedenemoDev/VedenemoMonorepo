# Testing Strategy

## Current phase

The first target is compilation and structural tests only.

The backend must be testable without running the UX.

## Later targets

- Command replay tests
- Parser tests
- Serialization compatibility tests
- Adapter integration tests
- CLI smoke tests
- Frontend typecheck/build tests

## CI split

Backend and frontend CI are intentionally separate.

```text
.github/workflows/backend-ci.yml
.github/workflows/frontend-ci.yml
```
