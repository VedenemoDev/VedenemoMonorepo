# Current Task

## Radial tree visualization wizard path

Status: executed.

### Goal

Add a `Radial tree` chart type to the existing model-instance visualization
wizard with the same runtime binding and data behavior as the implemented D3
`Tidy tree`, changing only the visualization layout.

### Scope

- Add `Radial tree` to the frontend chart-type registry.
- Reuse the existing tree eligibility, model-element binding, root-selection,
  Level 1 filtering, query loading, association-link loading, cycle guard, and
  tree-data transformation path.
- Render the selected tree data with a D3 radial tree layout inspired by
  <https://observablehq.com/@d3/radial-tree-component>.
- Keep visualization bindings runtime-only and frontend-only.
- Update implementation documentation and task bookkeeping.

### Acceptance Criteria

- The visualization wizard chart type step lists selectable `Tidy tree` and
  `Radial tree` options for eligible model-instance roots.
- `Radial tree` uses the same binding step and validation behavior as
  `Tidy tree`.
- Visualizing `Radial tree` fetches the same root-scoped instance data and
  association links as `Tidy tree`.
- The visualization step renders the result as a scrollable D3 radial SVG tree.
- Frontend build succeeds.

### Completion Notes

- Added `Radial tree` as a second chart type.
- Reused the existing tree binding and data-building flow.
- Added a D3 radial renderer using `d3.tree` with angular/radius coordinates
  and `d3.linkRadial`.
- Updated README, visualization docs, and current implementation architecture
  docs.
- Verified with `npm run build` in `vedenemo-ux`.
