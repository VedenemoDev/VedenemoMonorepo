# Current Task

## Add non-interactive terminal CLI load commands

Status: executed

### Goal

Add normal terminal `VedenemoCli` support for non-interactive one-shot loading
of model definition and model-instance data files.

The intended use cases are:

- `--mload <path_name_to_file.vdos>` loads a `.vdos` model file and exits.
- `--dload <path_name_to_file.vdmp>` loads a `.vdmp` model-instance dump file
  and exits.
- `--mload <path_name_to_file.vdos> --dload <path_name_to_file.vdmp>` loads
  the model first, then imports the dump into that loaded model, and exits.

This is terminal CLI functionality only. The browser UX virtual CLI is out of
scope because it is accessible only inside the browser UX.

### Scope

- Parse startup arguments in the normal terminal CLI entry point.
- Keep the existing interactive `mload` and `dload` commands working unchanged.
- Reuse the existing HTTP-backed CLI clients and load flows where practical, so
  the terminal CLI remains a thin HTTP/file-I/O client.
- Support explicit filesystem paths supplied as command parameters.
- Return deterministic process exit codes for successful loads, CLI usage
  errors, file read errors, backend/API errors, and import/precheck failures.
- Print concise terminal output suitable for scripts while preserving useful
  human diagnostics.
- Support `--force` for `.vdmp` imports where the dump model version is older
  than the currently loaded model version.
- Add terminal CLI tests for both successful and failing non-interactive paths.
- Update `README.md`, `docs/cli-reference.md`, and current implementation
  architecture documentation.

### Out Of Scope

- Browser UX virtual CLI changes.
- New backend endpoints or new import semantics unless existing endpoints are
  insufficient.
- Cloud snapshot or cloud dump selection by number/key for these startup flags.
- Interactive prompts during one-shot operation.
- Loading a `.vdmp` dump whose model version is newer than the currently loaded
  model, even when `--force` is supplied.
- Automatic rename or replacement handling for duplicate `.vdos` model
  `azName` conflicts.
- Changes to `.vdos` or `.vdmp` file formats.

### Acceptance Criteria

- Running `VedenemoCli --mload path/to/model.vdos` loads the model through the
  existing HTTP API and exits without opening the interactive prompt.
- Running `VedenemoCli --dload path/to/data.vdmp` imports the dump into a new
  model-instance root through the existing HTTP API and exits without opening
  the interactive prompt.
- Running `VedenemoCli --mload path/to/model.vdos --dload path/to/data.vdmp`
  loads the model and then imports the dump in the same process without opening
  the interactive prompt.
- `--dload` without a loaded target model fails with an actionable diagnostic.
- Duplicate `.vdos` model `azName` conflicts fail non-interactively with an
  actionable diagnostic.
- Older `.vdmp` dump versions fail by default, but succeed with `--force` when
  the currently loaded model version is newer and the existing import precheck
  otherwise permits the load.
- Newer `.vdmp` dump versions fail even when `--force` is supplied.
- Successful one-shot commands exit with code `0`.
- Usage and load failures exit with non-zero codes and include actionable
  diagnostics.
- Existing interactive commands and tests still pass.
- `mvn clean verify` succeeds.

### Completion Notes

- Added `VedenemoCli.run(String[] args)` and wired the terminal entry point to
  pass startup arguments through to the application.
- Added non-interactive parsing for `--mload <path>`, `--dload <path>`, and
  optional `--force`.
- `--mload` imports the `.vdos` file through the existing HTTP model import
  client, attaches the CLI session to the loaded model, and exits without
  opening the interactive prompt.
- Non-interactive `--mload` fails on duplicate model `azName` conflicts instead
  of prompting for a replacement name.
- Standalone `--dload` reads the target model `azName` from `.vdmp` metadata,
  verifies that the model is already loaded in the backend, prechecks the dump,
  imports it into a new model-instance root, and exits without opening the
  interactive prompt.
- Combined `--mload <model.vdos> --dload <data.vdmp>` loads the model first and
  then imports the dump into that loaded model in the same process.
- `--force` is accepted only with `--dload` and only confirms the existing
  older-dump-to-newer-model precheck path. Failed prechecks, including newer
  dump versions, still fail.
- Usage errors exit with code `2`; load, file, backend, and precheck failures
  exit with code `1`; successful one-shot loads exit with code `0`.
- Updated README, CLI reference, and current implementation architecture
  documentation.
- Verification passed: `mvn -q -pl vedenemo-cli -am test`, `mvn -q clean
  verify`, `git diff --check`, README disclaimer check, combined
  `--mload`/`--dload` CLI smoke, and separate `--mload` then standalone
  `--dload` CLI smoke against a local backend.
