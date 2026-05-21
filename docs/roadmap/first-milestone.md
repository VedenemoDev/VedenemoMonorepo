# First Milestone

## Goal

Create a compiling project skeleton that establishes the long-term boundaries of Vedenemo.

## Done when

- `mvn clean verify` succeeds from the repository root.
- `cd vedenemo-ux && npm ci && npm run build` succeeds.
- `vedenemo-core` has no direct third-party dependencies.
- `ModelStorage` SPI exists.
- `InMemoryModelStorage` adapter exists.
- CLI and app entry points exist but do not implement real behavior.

## Explicitly out of scope

- Real script parser
- REST API
- WebSocket/SSE
- Database persistence
- JSON serialization
- D3 visualization
- Kubernetes deployment
