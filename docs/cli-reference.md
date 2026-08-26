# Vedenemo CLI Reference

`VedenemoCli` is an HTTP-backed interactive command-line client. It creates a
backend session when it starts and removes that session when `exit` is used.

## Start The CLI

Build the backend first:

```bash
mvn clean verify
```

Start the backend in one terminal:

```bash
java -jar vedenemo-web-api/target/vedenemo-web-api-0.1.0-SNAPSHOT.jar
```

Start the CLI in another terminal:

```bash
java -cp vedenemo-cli/target/classes org.vedenemo.cli.VedenemoCli
```

The CLI reads the backend URL from `VEDENEMO_API_BASE_URL`. If the variable is
not set, it uses `http://127.0.0.1:8080`.

Example with a custom backend port:

```bash
VEDENEMO_API_BASE_URL=http://127.0.0.1:18080 \
  java -cp vedenemo-cli/target/classes org.vedenemo.cli.VedenemoCli
```

## Prompt

Without an attached model:

```text
VedenemoCli>
```

With an attached model:

```text
VedenemoCli[Example_Model]>
```

With an attached model and selected entity:

```text
VedenemoCli[Example_Model/Customer]>
```

## Commands

Command words are case-insensitive. Parameters remain case-sensitive, so
`attach Example_Model` and `attach example_model` are different model
identifiers.

In a real terminal session, the main command prompt keeps input history for the
current CLI process only. Arrow Up and Ctrl+P navigate to previous commands;
Arrow Down and Ctrl+N navigate forward.

Press Esc during an interactive prompt to cancel the current operation and
return to the normal CLI prompt without executing it.

The browser console at `/console` uses the same CLI-like command behavior. It
supports the same prompt flows for `add`, `attr add`, and `assoc add`; blank
Enter accepts prompt defaults, and Esc cancels the active prompt flow. In the
terminal CLI, `msave`, `snapshots`, and `mload` use local `.vdos` files. In the
browser console, the same command names use backend cloud snapshots when the
backend snapshot store is configured.

Runtime model-instance data editing remains HTTP/API-driven, but development
data preservation is available through `roots`, `dumps`, `dsave`, and `dload`.
In the terminal CLI these commands use local `.vdmp` files plus backend root
listing; in the browser console they use backend-managed root listing and cloud
dump storage when configured.

### `ping`

Checks backend connectivity through `GET /models/ping`.

Example:

```text
VedenemoCli>ping
Backend responded OK.
```

### `help`

Prints the available commands.

### `list`

Lists models from the backend in deterministic backend order.

Example:

```text
VedenemoCli>list
1. Example Model (Example_Model) version 1.0.0
2. Sales Model (Sales_Model) version 1.0.0
```

If no models exist:

```text
No models available.
```

### `add`

When no model is attached, adds a new model through the backend.

The CLI asks for a visible name and then suggests an ASCII `azName`. Press
Enter to accept the suggestion or type a replacement. Suggestions preserve
digits after the first ASCII letter, so `Model 2026 Draft` suggests
`Model_2026_Draft`.

Example:

```text
VedenemoCli>add
Model visible name: Example Model
Model azName [Example_Model]:
Attached to model Example_Model.
Added and attached model Example_Model.
VedenemoCli[Example_Model]>
```

New models are created with version `1.0.0`. After a successful add, the CLI
automatically attaches the current backend session to the created model.

When a model is attached, `add` creates a new entity in that model through the
backend command API.

Example:

```text
VedenemoCli[Example_Model]>add
Entity visible name: Customer Entity
Entity azName [Customer_Entity]:
Entity Customer_Entity added.
VedenemoCli[Example_Model]>
```

Attribute creation uses `attr add`, not `add`.

### `attach [N | azName]`

Attaches the current CLI session to an existing model and updates the backend
session selected model.

Attach by list number from the most recent `list` output:

```text
VedenemoCli>list
1. Example Model (Example_Model) version 1.0.0
VedenemoCli>attach 1
Attached to model Example_Model.
VedenemoCli[Example_Model]>
```

`attach N` requires a previous `list` command. The number always refers to the
most recent list output.

Attach by `azName`:

```text
VedenemoCli>attach Example_Model
Attached to model Example_Model.
VedenemoCli[Example_Model]>
```

Attach interactively:

```text
VedenemoCli>attach
Model number or azName: Example_Model
Attached to model Example_Model.
```

### `detach`

Clears the selected model for the current backend session. It also clears any
selected entity in the CLI context.

```text
VedenemoCli[Example_Model]>detach
Detached from model.
VedenemoCli>
```

If no model is attached, the CLI prints:

```text
No model is currently attached.
```

### `entities`

Lists entities in the attached model.

Example:

```text
VedenemoCli[Example_Model]>entities
1. Customer (Customer) active since 1.0.0
```

If no model is attached, the CLI prints:

```text
Attach a model before listing entities.
```

### `entity [N | azName]`

Selects an entity in the attached model for attribute operations.

Select by list number from the most recent `entities` output:

```text
VedenemoCli[Example_Model]>entities
1. Customer (Customer) active since 1.0.0
VedenemoCli[Example_Model]>entity 1
Selected entity Customer.
VedenemoCli[Example_Model/Customer]>
```

Select by `azName`:

```text
VedenemoCli[Example_Model]>entity Customer
Selected entity Customer.
VedenemoCli[Example_Model/Customer]>
```

Clear only the selected entity while keeping the model attached:

```text
VedenemoCli[Example_Model/Customer]>entity detach
Entity detached.
VedenemoCli[Example_Model]>
```

### `attributes`

Lists attributes in the selected entity. Output includes data type and lifecycle
version fields.

Example:

```text
VedenemoCli[Example_Model/Customer]>attributes
1. Email (Email) type TEXT active since 1.0.0
```

If no entity is selected, the CLI prints:

```text
Select an entity before listing attributes.
```

### `attr add`

Adds a new attribute to the selected entity through the backend command API.

The CLI asks for a visible name, suggests an ASCII `azName`, and asks for a data
type. Suggestions preserve digits after the first ASCII letter, so
`Attribute 2` suggests `Attribute_2`. Press Enter at the data type prompt to
use `TEXT`. Data type input accepts case-insensitive aliases such as `text`,
`number`, `url`, `data`, `date`, `time`, `datetime`, and `location`.

Example:

```text
VedenemoCli[Example_Model/Customer]>attr add
Attribute visible name: Email Address 2
Attribute azName [Email_Address_2]:
Attribute data type [TEXT]: url
Attribute Email_Address_2 added.
VedenemoCli[Example_Model/Customer]>
```

If the backend rejects the attribute, for example because the `azName` already
exists in the entity, the CLI prints the failure and keeps the current context:

```text
Attribute was not added: attribute add failed with HTTP status 400: ...
```

### `associations`

Lists model-level associations. If no entity is selected, the command lists all
associations in the attached model. If an entity is selected, it lists
associations touching that entity.

Example without an entity selected:

```text
VedenemoCli[Example_Model]>associations
Associations for model Example_Model:
1. orders (Customer_orders) OWNERSHIP Customer -> Order [0..*] active since 1.0.0
2. enrollment (Student_enrollment) RELATION Student -> Course [1..*] roles student[0..*] <-> course[1..*] active since 1.0.0
```

Example with an entity selected:

```text
VedenemoCli[Example_Model/Customer]>associations
Associations for entity Customer in model Example_Model:
1. orders (Customer_orders) OWNERSHIP Customer -> Order [0..*] active since 1.0.0
2. enrollment (Student_enrollment) RELATION Student -> Course [1..*] roles student[0..*] <-> course[1..*] active since 1.0.0
```

### `assoc add [ownership | reference | relation]`

Adds a model-level association through the backend command API.

Supported first-version kinds:

- `ownership`, rendered as a composition-style edge in the UX diagram
- `reference`, rendered as an aggregation-style edge in the UX diagram
- `relation`, rendered as a solid line with role and cardinality labels at both
  ends

Running plain `assoc add` prompts for the kind. The prompt accepts either the
written kind name or a numbered shortcut: `1` for `ownership`, `2` for
`reference`, and `3` for `relation`.

For directed ownership/reference associations, the CLI asks for source entity,
target entity, visible name, cardinality, and lastly `azName`. Source and
target can be exact entity `azName` values or numbers from the latest
`entities` output. Blank cardinality defaults to `1`.

Example:

```text
VedenemoCli[Example_Model]>entities
1. Customer (Customer) active since 1.0.0
2. Order (Order) active since 1.0.0
VedenemoCli[Example_Model]>assoc add ownership
Source entity number or azName: 1
Target entity number or azName: 2
Association visible name: orders
Association cardinality [1]: 0..*
Association azName [Customer_orders]:
Association Customer_orders added.
```

For bidirectional relations, the CLI asks for the first end entity, first role
name, first end cardinality, second end entity, second role name, second end
cardinality, visible name, and lastly `azName`. Blank cardinality defaults to
`1`.

Example:

```text
VedenemoCli[Example_Model]>assoc add relation
First end entity number or azName: Student
First end role name: student
First end cardinality [1]: 0..*
Second end entity number or azName: Course
Second end role name: course
Second end cardinality [1]: 1..*
Relation visible name: enrollment
Relation azName [Student_enrollment]:
Relation Student_enrollment added.
```

### `msave [N | azName] [outputPath]`

Saves a model to a UTF-8 Vedenemo Script file with the `.vdos` extension.

If no model selector is provided, `msave` uses the currently attached model. If a
selector is provided, it can be a model number from the latest `list` output or
a case-insensitive model `azName`.

If no output path is provided, the CLI prompts with an editable default based on
the model `azName`. When a `.vedenemo` directory exists under the directory
where the CLI was started, the default msave location is that directory:

```text
VedenemoCli[Example_Model]>msave
Output file [.vedenemo/Example_Model.vdos]:
Saved model Example_Model to /current/directory/.vedenemo/Example_Model.vdos.
```

When `.vedenemo` does not exist, the default remains the current working
directory:

```text
VedenemoCli[Example_Model]>msave
Output file [Example_Model.vdos]:
Saved model Example_Model to /current/directory/Example_Model.vdos.
```

Absolute output paths are used directly. Relative output paths are resolved
under `.vedenemo` when that directory exists; otherwise they are resolved under
the current working directory.

Examples:

```text
msave
msave 1
msave Example_Model
msave Example_Model export-file
msave 1 /tmp/example.vdos
```

If the selected output path has no extension, `.vdos` is appended. If the file
already exists, the CLI asks before overwriting it.

The `.vdos` content is backend-generated. It contains model metadata, an
authoritative command section, and a snapshot section used for readability and
validation.

### `snapshots`

Lists UTF-8 `.vdos` files from the `.vedenemo` directory under the directory
where the CLI was started. The list is sorted by file name and can be used by
number with `mload`.

Example:

```text
VedenemoCli>snapshots
1. Levykokoelma.vdos
2. Sales_Model.vdos
```

If `.vedenemo` does not exist, the CLI prints:

```text
No .vedenemo directory found at /current/directory/.vedenemo.
```

### `mload <path | snapshot-number>`

Loads a model from a UTF-8 `.vdos` file and imports it through the backend.

The path can be absolute or relative to the directory where the CLI was started.
If the path has no extension, `.vdos` is appended. For bare relative names,
the CLI first checks `.vedenemo`, so `mload Levykokoelma` loads
`.vedenemo/Levykokoelma.vdos` when that file exists.

After running `snapshots`, a numeric argument loads from the latest snapshot
list:

```text
VedenemoCli>snapshots
1. Levykokoelma.vdos
VedenemoCli>mload 1
Attached to model Levykokoelma.
Loaded model Levykokoelma from /current/directory/.vedenemo/Levykokoelma.vdos with 2 commands.
```

Example:

```text
VedenemoCli>mload ./exports/example
Attached to model Example_Model.
Loaded model Example_Model from /current/directory/exports/example.vdos with 2 commands.
VedenemoCli[Example_Model]>
```

After a successful load, the CLI automatically attaches to the loaded model.
Loaded commands become baseline model state and are not added to the current
session undo stack.

If the backend rejects the load because the model `azName` already exists, the
CLI asks for a replacement import `azName` or lets the user cancel:

```text
model load failed with HTTP status 409: ...
New model azName for import, or blank to cancel:
```

### `dumps`

Lists UTF-8 `.vdmp` model-instance data dump files from the `.vedenemo`
directory. The list is sorted by file name and can be used by number with
`dload`.

Example:

```text
VedenemoCli>dumps
1. Music_Source_root_v1_2_3_2026_08_21.vdmp
```

### `roots`

Lists process-local model-instance roots for the currently attached model. Use
this command to find the one-based root number, visible name, model version,
and root id before running `dsave`.

Example:

```text
VedenemoCli[Music]>roots
Model-instance roots for model Music:
1. Source root version 1.2.3 (00000000-0000-0000-0000-000000000000)
```

### `dsave [root-id | root-number | root-name] [outputPath]`

Saves exactly one model-instance root to a JSON `.vdmp` file. Attach a model
first. If the attached model has one loaded root, `dsave` can use it without a
selector. If multiple roots exist, provide a root id, a root number from the
latest `roots` listing, or an exact root visible name.

If no output path is provided, the CLI prompts with an editable `.vdmp` file
name. Relative paths use `.vedenemo` when that directory exists; absolute paths
are used directly. Existing files require overwrite confirmation.

Example:

```text
VedenemoCli[Music]>dsave
Output dump file [.vedenemo/Music_Source_root_v1_2_3_2026_08_21.vdmp]:
Saved model-instance root 00000000-0000-0000-0000-000000000000 to /current/directory/.vedenemo/Music_Source_root_v1_2_3_2026_08_21.vdmp.
```

### `dload <path | dump-number>`

Loads a `.vdmp` file into a new model-instance root under the currently
attached model. The corresponding model must already be loaded and attached.
The CLI runs a backend precheck first. Newer dumps are rejected before import;
older dumps require yes/no confirmation. A successful load reports created
record/link counts, skipped duplicate links, warnings, and failed insert
diagnostics.

## Browser Console Snapshot Commands

In `/console`, `msave`, `snapshots`, and `mload` use the backend-configured cloud
snapshot store instead of the terminal filesystem. The backend must be started
with `VEDENEMO_SNAPSHOT_STORE=gcs`, `VEDENEMO_GCS_PROJECT_ID`,
`VEDENEMO_GCS_BUCKET`, `VEDENEMO_GCS_PREFIX`, and optionally
`VEDENEMO_SNAPSHOT_SCOPE`.

Browser `msave` saves the attached model to a manually named cloud snapshot:

```text
VedenemoCli[Example_Model]>msave
Snapshot name: smoke
Saved model Example_Model to cloud snapshot Example_Model/smoke.vdos.
```

Browser `snapshots` lists snapshots in the configured scope and caches that
list for numeric loading:

```text
VedenemoCli[Example_Model]>snapshots
Cloud snapshots:
1. Example_Model/smoke.vdos - Example Model (Example_Model) version 1.0.0, 2 commands, saved 2026-07-28T18:30:00Z
```

Browser `mload` accepts either a snapshot key or a number from the latest
`snapshots` output:

```text
VedenemoCli[Example_Model]>mload 1
Loaded model Example_Model from cloud snapshot Example_Model/smoke.vdos.
```

If the imported model `azName` already exists, the browser console asks for a
replacement import `azName`, matching the terminal CLI mload flow.

Browser `roots`, `dumps`, `dsave`, and `dload` use backend-managed APIs and the
backend-configured cloud dump store. `roots` lists active model-instance roots
for the attached model, `dumps` lists stored `.vdmp` entries for the attached
model, `dsave` stores the selected runtime root as a named cloud dump, and
`dload` imports a stored cloud dump into a new model-instance root after the
same compatibility precheck used by terminal `.vdmp` loading.

When browser `dsave` is run without a dump name, it prompts with an editable
default dump name based on the attached model, selected root, model version, and
current date. Press Enter to accept the suggestion:

```text
VedenemoCli[Music]>dsave
Dump name [Music_Source_root_v1_2_3_2026_08_21]:
Saved model-instance root 00000000-0000-0000-0000-000000000000 to cloud dump Music/Music_Source_root_v1_2_3_2026_08_21.vdmp.
```

### `undo`

Asks the backend to undo the latest executed command for the current session.
Undo is stack-based: it applies only to the latest successful command. It can
undo entities created through attached-model `add`, attributes created through
`attr add`, and associations or relations created through `assoc add`.

```text
VedenemoCli[Example_Model]>undo
Undo completed: removed entity Customer from model Example_Model.
```

Attribute creation undo includes the model and entity context:

```text
VedenemoCli[Example_Model/Customer]>undo
Undo completed: removed attribute Email from entity Customer in model Example_Model.
```

If there is no command to undo:

```text
Nothing to undo.
```

### `exit`

Ends the backend session and exits the CLI.

```text
VedenemoCli>exit
```
