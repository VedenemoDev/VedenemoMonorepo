# Current Task

## Tidy tree root node selection by entity instance query

Status: executed.

### Goal

Extend the `Visualize Model Instance` flow for the `Tidy tree` chart so the
chart root can either remain a manually named synthetic chart root or be bound
to one selected model entity data instance.

### Scope

- Add a radio-button choice between writing a root node title and selecting a
  model entity data instance node.
- Keep manual root label mode compatible with the previous Tidy tree binding.
- Let selected-root mode choose the first Tidy tree entity level as the root
  entity node type.
- Let selected-root mode define a separate root label template.
- Let selected-root mode define one or more direct scalar comparison rows.
- Let selected-root mode add relationship criteria equivalent to the query
  console.
- AND all direct and relationship criteria through the existing entity `_query`
  endpoint.
- Resolve selected-root matches automatically and show the current match count.
- Disable visualization until selected-root mode matches exactly one entity
  instance.
- Render selected-root mode from the resolved entity instance as the first Tidy
  tree level, showing only descendants reachable through the existing binding.
- Allow explicit self-association recursion in finite Tidy tree bindings while
  still blocking non-recursive entity-type cycles.

### Acceptance Criteria

- The Tidy tree binding step exposes a radio-button choice between manual chart
  root label and selected model entity data instance root.
- Manual mode behaves as it did before this task.
- Entity-instance root mode lets the user build multiple ANDed scalar
  comparison rows using entity, attribute, operator, and value controls.
- Entity-instance root mode supports relationship criteria equivalent to the
  query console.
- Entity-instance root mode includes a separate label template for the resolved
  root node.
- Operator choices are constrained by the selected attribute data type in the
  same way as the query console.
- The UI automatically resolves and displays a footer label showing whether the
  current conditions match zero, one, or multiple instances.
- Visualization is disabled until entity-instance root mode resolves to exactly
  one instance.
- The rendered Tidy tree starts at the selected entity instance as the first
  Tidy tree level and includes only descendants reachable through the existing
  Tidy tree level/association binding.
- `npm run build` succeeds in `vedenemo-ux`.

### Completion Notes

- Added selected-root binding state to the Tidy tree runtime binding.
- Added selected-root UI controls for root entity, root label template, scalar
  comparisons, relationship criteria, and automatic match count feedback.
- Reused the existing `_query` API for root resolution without backend or core
  changes.
- Updated Tidy tree data building so selected-root mode renders the resolved
  entity instance directly as the chart root.
- Fixed recursive tree binding so self-associations such as
  `FamilyUnit_ChildFamilyUnits` can be added as additional finite levels, and
  rendering skips instance ids already present on the current path.
- Updated `docs/architecture_doc.md` to reflect the current visualization flow.
- Verified with `cd vedenemo-ux && npm run build`.
