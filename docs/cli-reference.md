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
`number`, `url`, and `data`.

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

### `undo`

Asks the backend to undo the latest executed command for the current session.
Undo is stack-based: it applies only to the latest successful command. Currently
this can undo an entity created through attached-model `add` and an attribute
created through `attr add`.

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
