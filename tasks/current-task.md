# Current Task

## Add Cloud Snapshot Save/Load For Browser Console

Status: executed.

### Goal

Let the browser `/console` use plain `save`, `snapshots`, and `load` commands
against backend-managed Google Cloud Storage `.vdos` snapshots, while keeping
terminal `VedenemoCli` `save`, `snapshots`, and `load` backed by the local
filesystem.

### Scope

- Add a Vedenemo-specific snapshot store SPI for `.vdos` snapshot artifacts.
- Add a Google Cloud Storage adapter in a separate infrastructure module.
- Keep Google Cloud SDK dependencies out of `vedenemo-core`,
  `vedenemo-model-api`, and `vedenemo-command-console`.
- Wire snapshot storage explicitly in web API composition from environment
  variables:
  - `VEDENEMO_SNAPSHOT_STORE=gcs`
  - `VEDENEMO_GCS_PROJECT_ID`
  - `VEDENEMO_GCS_BUCKET`
  - `VEDENEMO_GCS_PREFIX`
  - `VEDENEMO_SNAPSHOT_SCOPE`
- Add backend browser-console snapshot operations for:
  - saving the attached model to a manually named cloud snapshot;
  - listing snapshots in the configured scope;
  - loading a snapshot by latest listed number or snapshot key;
  - prompting for replacement model `azName` when loaded `.vdos` content
    conflicts with an existing model.
- Preserve terminal CLI local file behavior unchanged.
- Use backend server clock for snapshot saved timestamps in this first slice.
- Keep tests deterministic and not dependent on live GCP by default.

### Constraints

- First private development access boundary is private Tailscale/backend
  reachability; no per-user auth or sharing rules in this slice.
- Browser clients must not receive GCP credentials.
- Snapshot keys should be backend-owned identifiers so later authorization can
  be added without changing command syntax.
- One global bucket namespace is acceptable for this phase.

### Done Criteria

- `mvn -B verify` succeeds.
- Focused tests cover browser console cloud `save`, `snapshots`, duplicate
  `load` rename prompt behavior, and no-store error handling.
- `docs/architecture_doc.md`, `README.md`, `docs/cli-reference.md`,
  `tasks/backlog.md`, and `SESSION.md` reflect the implemented behavior.

### Completion Notes

- Added a pure Vedenemo snapshot SPI in `vedenemo-core-spi`.
- Added `vedenemo-storage-gcs` as the Google Cloud Storage adapter module.
- Browser console `save`, `snapshots`, and `load` now use backend-managed cloud
  snapshots when the web API snapshot store is configured.
- Terminal `VedenemoCli` local `.vdos` save/list/load behavior remains local
  filesystem-backed.
- Web API composition reads the snapshot store from environment variables and
  keeps GCP credentials on the backend side.
- Added focused command-console, web API, and CLI test-double coverage.
- Fixed the web API shaded JAR packaging to exclude signed dependency metadata
  introduced by the Google Cloud client dependencies.
- Verified with `mvn -B clean verify`, `mvn -B -pl vedenemo-web-api -am package
  -DskipTests`, and `mvn -B verify`.
