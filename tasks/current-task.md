# Current Task

## Add VAttribute Commands And CLI Entity Context

Status: executed.

### Goal

Extend the existing command execution path so users can add `VAttribute` items
to an existing `VEntity` through `VedenemoCli`, using the backend HTTP API in
the same command-oriented style already used for adding entities to a model.

This task also adds the internal attribute removal operation needed to undo
attribute creation. User-visible attribute removal as a separate edit operation
is deferred until it can be recorded and undone as its own command.

### Scope

- Add `CreateAttributeCommand`.
- Add internal `DeleteAttributeCommand` counterpart.
- Update `CommandExecutor` so create-attribute commands add attributes to an
  entity in the selected model.
- Undo must remain stack-based and apply only to the latest successfully
  executed command.
- Undo of `CreateAttributeCommand` derives `DeleteAttributeCommand` at undo
  time from the fully qualified target path stored in the create command.
- Add `POST /sessions/{uuid}/commands/create-attribute`.
- Add read-only listing endpoints:
  - `GET /models/{modelAzName}/entities`
  - `GET /models/{modelAzName}/entities/{entityAzName}/attributes`
- Add CLI entity context and commands:
  - `entities`
  - `entity [N | azName]`
  - `entity detach`
  - `attributes`
  - `attr add`
- Attribute data type input accepts case-insensitive aliases and defaults blank
  or missing input to `TEXT`.

### Verification

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model
- add entity
- select entity
- add attribute
- list attributes
- undo an attribute creation
- exit

### Completion Notes

- Added `CreateAttributeCommand` and internal `DeleteAttributeCommand`.
- Updated `CommandExecutor` to create attributes in the selected model/entity
  and undo attribute creation by deriving the internal delete counterpart.
- Added `POST /sessions/{uuid}/commands/create-attribute`.
- Added read-only entity and attribute listing endpoints.
- Added CLI entity context and `attr add` flow.
- Added focused core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed.
