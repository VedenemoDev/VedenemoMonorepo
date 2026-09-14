# Current Task

## Add reusable tree-chart aggregate labels for numeric descendant values

Status: executed

### Goal

Implement the planned proof-of-concept for reusable aggregate and derived label
support across tree-structured visualizations.

Tree of Life, Tidy tree, and Radial tree share the same tree binding/data path,
so the shared aggregate evaluator should be usable by all three chart renderers
while preserving chart-specific rendering.

### Scope

- Add a reusable tree label template expression for numeric descendant
  aggregates.
- Support `min`, `max`, `avg`, `median`, `variance`, and `sum`.
- Offer aggregate label insertions only for reachable `NUMERIC` descendant
  attributes.
- Preserve existing free text, `{id}`, and direct attribute label templates.
- Evaluate aggregate labels in the common tree data builder used by Tree of
  Life, Tidy tree, and Radial tree.
- Mark the backlog item executed after implementation and verification.

### Out Of Scope

- Backend API changes unless current UX data is insufficient.
- Database-backed aggregate execution.
- Non-numeric aggregate functions.
- Persisted visualization configuration beyond the current in-memory wizard
  binding state.

### Acceptance Criteria

- Existing tree chart label templates continue to work.
- Aggregate template options appear only for reachable `NUMERIC` descendant
  attributes.
- A label can combine free text, ordinary placeholders, and more than one
  aggregate expression.
- Empty or missing numeric value sets render predictably without crashing.
- Tidy tree, Radial tree, and Tree of Life can all render labels resolved by
  the shared aggregate evaluator.
- `cd vedenemo-ux && npm run build` succeeds.
- `mvn clean verify` succeeds from the repository root.

### Completion Notes

- Added reusable tree label aggregate placeholders using
  `{min:Entity.attribute}`, `{max:Entity.attribute}`, `{avg:Entity.attribute}`,
  `{median:Entity.attribute}`, `{variance:Entity.attribute}`, and
  `{sum:Entity.attribute}` syntax.
- Aggregate placeholder insertion is offered only for reachable descendant
  attributes whose metadata data type is `NUMERIC`.
- Existing free text, `{id}`, and direct `{attribute}` label templates remain
  supported.
- Moved tree label rendering through the shared tree data builder used by Tidy
  tree, Radial tree, and Tree of Life.
- Empty or missing aggregate input values render as `n/a`.
- No backend API or core model changes were needed.
- `npm run build` succeeded in `vedenemo-ux`.
- `mvn clean verify` succeeded from the repository root.
