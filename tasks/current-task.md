# Current Task

## Add Model-Level `ValueSet` Constraint Support

Status: executed.

### Goal

Introduce a model-level `ValueSet` concept that can restrict allowed values for
entity attributes without adding a new `DataType` and without moving domain
constraint semantics into UX-specific configuration.

A `ValueSet` is a named, reusable finite set of allowed values. It belongs to a
model and can be referenced by compatible attributes in that model.

### Scope

- Add pure-JDK model support for model-level `ValueSet` definitions and
  entries.
- Support `TEXT`, `NUMERIC`, `DATE`, and `TIME` value sets in the first slice.
- Store the entry technical value as the actual instance value.
- Keep the entry visual name as display metadata.
- Allow creating an attribute with an optional `valueSet` reference.
- Allow attaching a compatible `ValueSet` to an existing attribute.
- Validate that referenced value sets exist in the same model.
- Validate data-type compatibility between attributes and referenced value
  sets.
- Enforce `ValueSet` membership for future instance create/update operations.
- Enforce `ValueSet` membership during `.vdmp` import.
- Preserve `ValueSet` definitions, entries, and attribute references in
  `.vdos` export/import.
- Expose `ValueSet` metadata to model consumers through existing model/API
  description flows.

### Out Of Scope

- New `ENUM` or user-defined enum data types.
- `URL`, `DATA`, `DATETIME`, and `LOCATION` value sets.
- Dynamic, database-backed, hierarchical, or entity-scoped value sets.
- Editing, removing, renaming, or reordering existing `ValueSet` entries.
- Retroactive validation or migration of existing instance data when a
  `ValueSet` is attached to an existing attribute.
- Deprecating individual `ValueSet` entries.
- UX-specific widget definitions.
- Treating `ValueSet` entries as entities with their own identity or lifecycle.

### Acceptance Criteria

- A model can define a named `ValueSet`.
- A `ValueSet` contains a finite collection of allowed values.
- Each entry has a stable technical value and a human-readable visual name.
- The entry technical value is the stored instance value.
- A `ValueSet` has a value type against which attribute compatibility can be
  checked.
- `TEXT`, `NUMERIC`, `DATE`, and `TIME` value sets are supported.
- A compatible attribute can reference a `ValueSet` at creation time.
- A compatible existing attribute can be updated to reference a `ValueSet`.
- Multiple attributes and entities can reuse the same `ValueSet`.
- Future instance create/update operations reject values outside the referenced
  `ValueSet`.
- `.vdmp` import rejects values outside the referenced `ValueSet`.
- Model consumers can discover an attribute's referenced `ValueSet` and its
  entries.
- `.vdos` export/import preserves `ValueSet` definitions, entries, and
  attribute references.
- `.vdos` import rejects references to undefined `ValueSet` definitions.
- No visualization- or UX-specific behavior is required in the core
  `ValueSet` concept.
- Backend verification succeeds.

### Completion Notes

- Added pure-JDK `ValueSet` and `ValueSetEntry` model types.
- Added model-level value-set storage and optional `VAttribute.valueSetAzName`.
- Added command support for creating value sets and attaching them to existing
  compatible attributes.
- Added undo counterparts for create-value-set and set-attribute-value-set.
- Added `.vdos` export/import and snapshot validation for value sets and
  attribute references.
- Added instance create/update/query normalization checks for constrained
  attributes.
- Added `.vdmp` precheck rejection for values outside referenced value sets.
- Exposed value sets and attribute references through HTTP model and runtime
  API description responses.
- Added CLI/browser-console flows for `vset add` and `attr vset`.
- Updated frontend API-description types for value-set metadata.
- Updated README, CLI reference, current implementation architecture docs, and
  backlog history.
- Verification passed with `mvn -q clean verify` and
  `cd vedenemo-ux && npm run build`.
