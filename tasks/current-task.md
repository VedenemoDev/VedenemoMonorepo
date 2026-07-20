# Current Task

## Add CLI Save And Load For Vedenemo Script Files

Status: executed.

### Goal

Add `save` and `load` commands to `VedenemoCli` using a new text-based
Vedenemo Script file format with the `.vdos` extension.

`save` exports a selected model from the backend through HTTP, including model
metadata, the full model structure, and executed command history, then writes
the result as UTF-8 text to a local file.

`load` reads a `.vdos` file from the local filesystem and sends it to the
backend so the backend can recreate the model as baseline state.

### Scope

- Introduce backend-owned `.vdos` script serialization and parsing.
- Keep the CLI responsible only for model/path selection, UTF-8 file I/O, and
  user-facing messages.
- Use command lines plus a final model snapshot, with commands authoritative and
  snapshot used for validation/readability.
- Introduce model-level command history for export so `.vdos` output is not tied
  to current CLI session lifetime.
- Add HTTP endpoints:

```text
GET /models/{modelAzName}/script
POST /models/script
```

- Add CLI commands:

```text
save [N | azName] [outputPath]
load <path>
```

- Use hybrid save path handling: inline output path when provided, otherwise
  prompt with editable default `<modelAzName>.vdos`.
- Append `.vdos` to save/load paths when no extension is provided.
- Prompt before overwriting an existing local save target.
- On duplicate model `azName` during load, reject first and offer a rename retry
  flow.
- Automatically attach to a successfully loaded model.
- Treat loaded commands as baseline state with no undo available from the load
  operation.

### Verification

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a local backend plus CLI smoke test that creates a model,
saves it, loads it, and verifies the loaded model data is available.

### Completion Notes

- Added pure-JDK core `.vdos` script import/export support.
- Added a model-level `ModelCommandJournal` so export uses model command history
  instead of session-only undo history.
- Updated command execution and undo so successful model-targeting commands are
  recorded in the journal and undone commands are removed from it.
- Added HTTP endpoints:

```text
GET /models/{modelAzName}/script
POST /models/script
```

- Added CLI `save [N | azName] [outputPath]` with attached-model default,
  list-number and `azName` target resolution, `.vdos` extension handling, prompt
  fallback, UTF-8 writes, and overwrite confirmation.
- Added CLI `load <path>` with `.vdos` extension handling, UTF-8 reads,
  duplicate import rename retry, and automatic attach to the loaded model.
- Loaded `.vdos` commands are imported as baseline model state and are not added
  to the current session undo stack.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- Added focused core, web API, and CLI tests.
- `mvn -B clean verify` passed.
