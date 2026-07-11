# Current Task

## Create VEntity and VAttribute classes into vedenemo-model-api

Status: completed.

### Goal

Add the first model-structure classes that will later be bound under
`ModelRoot`.

This task should introduce domain model types only. Do not add REST endpoints,
UX changes, persistence, parser behavior, or model-root binding yet.

### Domain Model Changes

Add `Versionable`, an abstract base class for model elements with lifecycle
version metadata:

- `activeSince`: required `ModelVersion` since which the element is considered
  active.
- `deprecatedSince`: optional `ModelVersion` since which the element is
  considered deprecated.

When `VEntity` or `VAttribute` is created for a model, `activeSince` is expected
to come from the current `ModelVersion` of the owning `ModelRoot`. The actual
binding to `ModelRoot` is not implemented in this task; callers pass the
`ModelVersion` explicitly for now.

Add `DataType`, a Java enum:

```java
public enum DataType {
    TEXT,
    NUMERIC,
    URL,
    DATA
}
```

Add `VAttribute`, a model attribute class extending `Versionable`, with these
fields:

- `azName`: ASCII Vedenemo attribute identifier. Preserve the original case as
  entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the attribute. UTF-8 string. Must be
  non-blank. Uniqueness is not enforced.
- `type`: `DataType`.

`VAttribute` should not know which entity hosts it and should not enforce
attribute-name uniqueness by itself. Attribute `azName` uniqueness belongs to
the hosting `VEntity`.

Add a `VEntity` class with these fields:

- `azName`: ASCII Vedenemo entity identifier. Preserve the original case as
  entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the entity. UTF-8 string. Must be
  non-blank.
- `attributes`: ordered collection of `VAttribute` instances belonging to the
  `VEntity`. It must preserve original insertion order.

`VEntity` extends `Versionable`.

`VEntity` should provide explicit operations for managing attributes:

- add a `VAttribute`
- remove a `VAttribute` by attribute `azName`
- remove a `VAttribute` by instance
- list attributes as a read-only copy in insertion order

`VEntity` must enforce attribute `azName` uniqueness case-insensitively.
`Example` and `example` conflict, but original submitted casing is preserved.

`VEntity` itself should not enforce entity-name uniqueness within a model.
Entity uniqueness will be handled later by the future container that binds
entities under `ModelRoot` or another model-level aggregate.

`VAttribute` and `VEntity` should be immutable after construction in this first
iteration. The exception is `VEntity`'s explicit attribute-management methods,
which may add and remove attributes while a model is under construction. Later
release/deprecation rules are out of scope for this task.

### Constraints

- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak web, JSON, Javalin, or adapter types into core or SPI.
- Keep the new model types in pure JDK `vedenemo-model-api`.
- Keep explicit constructor wiring.
- Reuse the existing `ModelRoot` `azName` validation rules.

### Tests / Verification

Add focused model API tests if practical.

Minimum test coverage:

- `VAttribute` accepts valid data.
- `VAttribute` rejects invalid `azName`.
- `VAttribute` rejects blank `visName`.
- `VAttribute` rejects missing `DataType`.
- `VEntity` accepts valid data.
- `VEntity` preserves attribute insertion order.
- `VEntity` rejects duplicate attribute `azName`.
- `VEntity` rejects case-only duplicate attribute `azName`.
- `VEntity` can remove an attribute by `azName`.
- `VEntity` can remove an attribute by instance.
- `VEntity.attributes()` returns a read-only copy.
- lifecycle versions reject invalid combinations if lifecycle ordering is
  invalid.

At minimum, run:

```bash
mvn -B clean verify
```

### Architecture Documentation

After implementation and successful verification, update
`docs/architecture_doc.md` in the same change.

The update should document only the concrete implementation that exists after
the task is complete:

- `Versionable`
- `DataType`
- `VAttribute`
- `VEntity`
- `VEntity` attribute ordering and uniqueness behavior

### Resolved Planning Decisions

- `deprecatedSince` is optional.
- `activeSince` is required.
- `activeSince` is supplied from the current `ModelVersion` of the owning
  `ModelRoot` when entities or attributes are added to a model.
- When present, `deprecatedSince` must be strictly later than `activeSince`.
  Equal versions are invalid.
- `VEntity` removal should support both `azName` and `VAttribute` instance.
- `VEntity.attributes()` should expose only a read-only `List<VAttribute>` copy.
- `VAttribute` and `VEntity` should be immutable after construction, except for
  explicit `VEntity` attribute add/remove methods during model construction.

### Completion Notes

- Added `Versionable`, `DataType`, `VAttribute`, and `VEntity` to
  `vedenemo-model-api`.
- Extracted shared model text validation so `ModelRoot`, `VEntity`, and
  `VAttribute` use the same `azName` and `visName` rules.
- Made `ModelVersion` comparable so lifecycle ordering can be validated.
- Added focused model API tests for valid data, invalid names, blank display
  names, missing data type, attribute order, duplicate attributes, removal
  operations, read-only snapshots, and invalid lifecycle versions.
- Updated `docs/architecture_doc.md` to reflect the current concrete
  implementation.
- `mvn -B clean verify` passed.
