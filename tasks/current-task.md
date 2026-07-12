# Current Task

## Adding support for adding new models and listing existing models to VedenemoCli

Status: executed.

### Goal

Add first useful model-management commands to `VedenemoCli` while preserving the
existing HTTP-backed session startup and cleanup behavior.

The CLI should be able to:

- list existing models from the backend
- add a new model through the backend HTTP API
- attach the current CLI session to one listed model
- detach the current CLI session from the attached model
- show help for available commands

### CLI Commands

All previously available CLI behavior must keep working:

- startup creates a backend session
- empty line returns a new prompt
- `exit` cleans up the backend session and exits

Add these new commands.

#### `list`

Lists all currently added / loaded models from the backend as a numbered list.
Each row should show running number, `visName`, `azName`, and version. If there
are no models, print a clear message.

#### `attach [N | azName]`

Associates the current CLI session with an existing model.

- `attach N` attaches by the running number from the latest model list.
- `attach azName` attaches by model `azName`.
- `attach` with no argument asks the user for a number or `azName`.
- Successful attach must update backend `Session.selectedModelAzName`.
- `attach N` always refers to the most recent `list` output and must not fetch
  the model list automatically if no list exists.
- After successful attach, the prompt changes to `VedenemoCli[azName]>`.

#### `detach`

Detaches the current CLI session from the previously attached model.

- Successful detach must update backend `Session.selectedModelAzName`.
- Only the correctly spelled `detach` command is supported.

#### `add`

Adds a new model through the backend HTTP API.

Interactive flow:

1. Ask for `visName`.
2. Generate a valid ASCII `azName` suggestion from the entered `visName`.
3. Ask for `azName`, showing the suggestion.
4. If the user presses Enter without typing a replacement, use the suggestion.
5. If the user types a replacement, use the typed value.
6. Create the model with version `1.0.0`.
7. After successful creation, automatically attach the CLI session to the new
   model, update backend `Session.selectedModelAzName`, and update the prompt
   to `VedenemoCli[azName]>`.

#### `help`

Lists all available commands with short explanations.

### Backend / HTTP Scope

Use existing model endpoints:

- `GET /models/list`
- `POST /models/add`

Add backend session-selection endpoints:

- `PUT /sessions/{uuid}/selected-model`
- `DELETE /sessions/{uuid}/selected-model`

HTTP and JSON details must stay in `vedenemo-web-api`.

### Documentation

After implementation:

- Create `docs/cli-reference.md`.
- Update `README.md` with a short link to the CLI reference.
- Update `docs/architecture_doc.md` if component responsibilities or runtime
  flows materially change.

### Tests / Verification

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a local smoke test with the backend JAR and non-interactive
CLI input.

### Resolved Planning Decisions

- `attach` and `detach` must update backend `Session.selectedModelAzName`
  through HTTP endpoints.
- Only `detach` is supported. Do not add typo alias `detatch`.
- For `attach N`, `N` always refers to the most recent `list` output.
- `attach N` must not fetch the model list automatically if no list exists.
- After `add` creates a model, auto-attach must update backend selected model
  state as well as local CLI prompt state.
- Create a separate CLI reference document now and link to it from `README.md`.

### Completion Notes

- Added CLI model-management commands: `list`, `add`, `attach`, `detach`, and
  `help`.
- Added CLI HTTP model client support for `GET /models/list` and
  `POST /models/add`.
- Extended CLI session HTTP support for selecting and clearing the backend
  session selected model.
- Added backend selected-model endpoints:
  - `PUT /sessions/{uuid}/selected-model`
  - `DELETE /sessions/{uuid}/selected-model`
- Added focused CLI and web API tests for the new command and endpoint
  behavior.
- Added `docs/cli-reference.md`, linked it from `README.md`, and updated the
  current implementation architecture document.
- `mvn -B clean verify` passed during implementation; final verification was
  run after documentation updates.
