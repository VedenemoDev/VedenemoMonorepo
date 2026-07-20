# Current Task

## Improve CLI azName Suggestions And Undo Feedback

Status: executed.

### Goal

Improve two CLI usability details:

- `azName` suggestions should preserve useful numeric suffixes from visible
  names, so `Attribute 2` suggests `Attribute_2` instead of `Attribute`.
- `undo` output should describe what kind of operation was undone instead of
  only printing `Undo completed.`

### Scope

- Allow ASCII digits after the first ASCII letter for all `azName` values in
  `ModelRoot`, `VEntity`, and `VAttribute`.
- Keep `azName` starting-character validation unchanged: names must start with
  an ASCII letter.
- Keep hyphens invalid.
- Update CLI `azName` suggestion behavior so digit runs are preserved after the
  suggestion has started with an ASCII letter.
- Enrich core undo results with stable HTTP/API slug command identifiers:
  `create-entity` and `create-attribute`.
- Update `POST /sessions/{uuid}/commands/undo` to return target details for a
  successful undo.
- Update CLI undo output:

```text
Undo completed: removed entity Customer from model Example_Model.
Undo completed: removed attribute Email from entity Customer in model Example_Model.
```

### Verification

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model with numeric suffix
- add entity with numeric suffix
- select entity
- add attribute with numeric suffix
- undo attribute creation and verify specific undo output
- undo entity creation and verify specific undo output
- exit

### Completion Notes

- Updated shared `azName` validation to allow ASCII digits after the first
  ASCII letter while still rejecting leading digits and hyphens.
- Updated CLI `azName` suggestions to preserve numeric suffixes.
- Replaced generic undo success with richer undo result metadata using stable
  HTTP/API slug names.
- Updated the undo HTTP response and CLI output to report the undone operation
  and target.
- Added focused model/core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed.
