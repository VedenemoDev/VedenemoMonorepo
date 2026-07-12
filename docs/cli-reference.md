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

Adds a new model through the backend.

The CLI asks for a visible name and then suggests an ASCII `azName`. Press
Enter to accept the suggestion or type a replacement.

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

Clears the selected model for the current backend session.

```text
VedenemoCli[Example_Model]>detach
Detached from model.
VedenemoCli>
```

If no model is attached, the CLI prints:

```text
No model is currently attached.
```

### `exit`

Ends the backend session and exits the CLI.

```text
VedenemoCli>exit
```
