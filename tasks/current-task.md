# Current Task

## Tidy tree manual root Level 1 filtering

Status: executed.

### Goal

Extend `Tidy tree` manual-root mode so the first entity level below the
synthetic chart root can be filtered with query-style scalar and relationship
criteria before the tree is rendered.

### Scope

- Keep manual-root mode selected by `Write root node title`.
- Keep the existing synthetic `Chart root label` input.
- Keep the existing Level 1 entity selection and label template controls.
- Add an explicit `Filter Level 1 nodes` toggle inside the Level 1 card.
- When the Level 1 filter is enabled, allow multiple scalar comparison rows.
- When the Level 1 filter is enabled, allow relationship criteria with required
  related-attribute comparisons.
- Combine all enabled Level 1 criteria with logical `AND`.
- Resolve Level 1 match counts automatically.
- Disable visualization when an enabled Level 1 filter matches zero instances.
- Keep filtering scoped to Level 1 only.
- Preserve selected entity-instance root behavior.

### Acceptance Criteria

- In `Write root node title` mode, Level 1 exposes an explicit filter toggle in
  the Level 1 card.
- A user can add multiple ANDed scalar comparisons for Level 1.
- A user can add relationship criteria for Level 1 equivalent to the query
  console, with an attribute comparison required for each relationship
  criterion.
- Operators are constrained by attribute data type in the same way as the query
  console.
- The UI automatically shows how many Level 1 instances match the current
  filter.
- If the enabled filter matches zero Level 1 instances, visualization is
  disabled and the UI explains that the start condition matched no results.
- Visualization renders only matching Level 1 instances under the synthetic
  root, then renders deeper descendants according to the existing binding.
- Deeper levels do not expose filtering controls in this task.
- If no Level 1 filter is enabled, manual-root mode behaves as it did before
  this task.
- Selected entity-instance root mode remains unchanged.
- `npm run build` succeeds in `vedenemo-ux`.

### Completion Notes

- Added runtime-only Level 1 filter state to Tidy tree binding levels.
- Added an explicit Level 1 filter toggle and query-style scalar/relationship
  criteria controls inside the Level 1 card.
- Reused the existing entity `_query` endpoint for match counting and render
  data.
- Added automatic match-count feedback for Level 1 filters.
- Blocked visualization when an enabled Level 1 filter matches zero instances.
- Kept deeper-level traversal semantics unchanged.
- Verified with `cd vedenemo-ux && npm run build`.
