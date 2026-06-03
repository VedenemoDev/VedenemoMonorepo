# Backend First Steps Plan

## Purpose

This plan records the recommended next backend direction after the successful
Firebase Hosting setup for `vedenemo-ux`.

The current best architecture is intentionally split:

```text
Firebase Hosting -> static Vite UX
Backend -> separate deployment target later
```

The UX can keep deploying automatically through Firebase Hosting while the
backend remains a clean Java/Maven skeleton until it has a real API contract.

## Current State

- Backend CI runs `mvn -B clean verify`.
- Frontend CI runs the Vite build.
- `Deploy UX` automatically deploys Firebase Hosting when `vedenemo-ux/**` or
  `.github/workflows/deploy-ux.yml` changes on `main`.
- Backend modules are still in the first milestone phase.
- No REST API, WebSocket API, database, persistence format, or cloud backend
  runtime has been chosen.

## Recommendation

Do not bind a cloud backend deployment into CI yet.

Keep the current split:

```text
push -> backend CI: mvn verify
push -> frontend CI: npm build
push touching UX -> Firebase Hosting deploy
push touching backend -> no backend deploy yet
```

Add backend deployment only after the backend has a minimal public runtime
contract.

## Why Not Deploy Backend Yet

The backend is not currently a service. It is a multi-module Java skeleton with
core, SPI, memory storage, CLI, and app wiring.

Deploying it now would force premature decisions about:

- HTTP framework
- process model
- container packaging
- runtime configuration
- secrets
- logging
- health checks
- rollbacks
- storage
- WebSocket support
- public/private network exposure

Those decisions should follow from the first real backend behavior, not precede
it.

## Near-Term Backend Work

Keep the next work focused on backend structure and behavior:

1. Add structural backend tests.
   - Test `CommandExecutor`.
   - Test `InMemoryModelStorage`.
   - Add minimal wiring tests for app or CLI entry points.

2. Add dependency boundary checks.
   - Confirm `vedenemo-core` has no direct third-party dependencies.
   - Keep adapters behind Vedenemo-owned SPI.
   - Avoid dependency injection frameworks.

3. Define one minimal model operation.
   - Keep it pure Java.
   - Route it through the core command path.
   - Store/retrieve through `ModelStorage`.
   - Avoid parser, REST, WebSocket, database, and JSON work at this stage.

4. Decide the first runtime contract only after core behavior exists.
   - CLI-only is enough until a remote UX needs data.
   - HTTP should be introduced as an adapter/runtime boundary, not inside core.

## Later Backend Deployment Options

### Option A: Cloud Run

Likely first public cloud backend target.

Use when:

- A minimal REST API exists.
- The backend can be containerized cleanly.
- Public HTTPS access is wanted.
- Scale-to-zero and low baseline cost are important.

Expected CI shape:

```text
push main touching backend -> mvn verify -> build container -> deploy Cloud Run
push main touching UX -> npm build -> deploy Firebase Hosting
```

Cloud Run is a better first cloud target than Kubernetes for this project
because it avoids cluster operations while the backend is still small.

### Option B: Private Tailscale or Home Server

Useful for private experiments, not preferred for public production.

Use when:

- Only the developer needs access.
- The backend is still exploratory.
- Public uptime, TLS, monitoring, and rollback are not yet product concerns.

This can stay outside GitHub CI initially. Manual deploys are acceptable for
private experiments.

### Option C: Kubernetes

Defer until orchestration is justified.

Use only after there is a clear need for:

- multiple backend services
- long-running workers
- advanced networking
- custom scheduling
- stronger operational isolation

Kubernetes should not be the default first backend runtime.

## UX and Backend Integration Shape

Firebase Hosting should remain responsible only for static frontend delivery.

When the backend exists, the UX can call it through:

```text
Vite UX -> HTTPS REST backend
```

Later, if needed:

```text
Vite UX -> WebSocket backend
```

The UX hosting choice does not force the backend hosting choice.

## CI Policy

Keep frontend and backend independently releasable.

Current CI/deploy split is good:

- backend CI proves backend modules compile and pass tests
- frontend CI proves the UX builds
- deploy UX publishes static frontend only

Future backend deploy should be added as a separate workflow, for example:

```text
.github/workflows/backend-ci.yml
.github/workflows/frontend-ci.yml
.github/workflows/deploy-ux.yml
.github/workflows/deploy-backend.yml
```

`deploy-backend.yml` should not be added until:

- there is a backend runtime module
- the runtime has a health check
- the runtime has a minimal API contract
- configuration and secrets are understood
- local smoke testing is possible

## Cost Posture

Keep the baseline inexpensive:

- Firebase Hosting for static UX.
- No always-on backend until needed.
- Prefer scale-to-zero backend runtime when moving to cloud.
- Avoid load balancers and Kubernetes until justified.

This keeps the current monthly cost low while leaving a straightforward path to
Cloud Run later.

## Immediate Next Step

The next backend step should be tests and one small core behavior, not cloud
deployment.

Suggested next implementation task:

```text
Add focused backend tests for CommandExecutor and InMemoryModelStorage, then add
one minimal command-backed model operation through the existing SPI boundary.
```
