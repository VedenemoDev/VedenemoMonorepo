# Current Task

## Development-time model instance data persistence as file dumps

Status: executed.

### Goal

Implement development-time model-instance data dumps using `.vdmp` JSON files,
with terminal CLI local file support, browser console cloud dump support,
backend import/export/precheck routes, and pure core-owned dump behavior.

### Scope

- Keep `.vdmp` as development-time dump support, not final durable persistence.
- Export and import one model-instance root per dump.
- Keep core dump structures and import/export rules free of third-party
  dependencies.
- Keep JSON mapping and HTTP DTO handling in `vedenemo-web-api`.
- Keep terminal CLI file I/O local to `vedenemo-cli`.
- Keep browser console storage behind backend-managed dump storage.
- Rename model snapshot commands from `save`/`load` to `msave`/`mload` without
  backward-compatible aliases.

### Acceptance Criteria

- Terminal CLI can list `.vdmp` files with `dumps`, save a selected
  model-instance root with `dsave`, and import a dump into a new root with
  `dload`.
- Browser console exposes `dumps`, `dsave`, and `dload` through the configured
  backend dump store.
- Backend exposes root-scoped dump export, submitted dump precheck/import, and
  cloud dump list/save/precheck/load routes.
- Dump import validates model/version/schema compatibility before import,
  requires confirmation for older-dump-to-newer-model loads, rejects newer
  dumps, omits dump-level `null` values before create validation, remaps
  dump-local ids, skips duplicate links, and reports counts/diagnostics.
- `.vdmp` format documentation and current implementation architecture docs are
  synchronized.

### Completion Notes

- Added pure core `.vdmp` records and `ModelInstanceDumpService`.
- Added `ModelInstanceDumpStore` SPI and GCS dump-store adapter.
- Added `/data/{modelAzName}/roots/{instanceRootId}/dump`,
  `/data/{modelAzName}/dumps/_precheck`, `/data/{modelAzName}/dumps`, and
  browser cloud dump routes under `/data/{modelAzName}/dumps`.
- Renamed model snapshot commands to `msave` and `mload` across terminal CLI,
  browser console, tests, and docs.
- Added terminal CLI local `.vdmp` `dumps`, `dsave`, and `dload` support.
- Added browser console cloud `dumps`, `dsave`, and `dload` support.
- Documented the implemented `.vdmp` JSON format in
  `docs/model-instance-dump-format.md`.
- Verified with `mvn -B verify`.
