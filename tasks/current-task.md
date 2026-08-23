# Current Task

## Tree of life visualization wizard path

Status: executed.

### Goal

Add a `Tree of life` chart type to the existing model-instance visualization
wizard with the same runtime binding and data behavior as the implemented D3
tree charts, changing only the visualization layout and skipping branch-length
support for now.

### Scope

- Add `Tree of life` to the frontend chart-type registry.
- Reuse the existing tree eligibility, model-element binding, root-selection,
  Level 1 filtering, query loading, association-link loading, cycle guard, and
  tree-data transformation path.
- Render the selected tree data with a D3 radial cluster layout inspired by
  <https://observablehq.com/@d3/tree-of-life>.
- Skip the Observable example's branch length property for this slice.
- Keep visualization bindings runtime-only and frontend-only.
- Update implementation documentation and task bookkeeping.

### Acceptance Criteria

- The visualization wizard chart type step lists selectable `Tidy tree`,
  `Radial tree`, and `Tree of life` options for eligible model-instance roots.
- `Tree of life` uses the same binding step and validation behavior as the
  existing tree charts.
- Visualizing `Tree of life` fetches the same root-scoped instance data and
  association links as the existing tree charts.
- The visualization step renders the result as a scrollable D3 radial cluster
  SVG tree with leaf labels on a common rim.
- No branch-length binding or data property is introduced.
- Frontend build succeeds.

### Completion Notes

- Added `Tree of life` as a third chart type.
- Reused the existing tree binding and data-building flow.
- Added a D3 radial cluster renderer using `d3.cluster`, constant-depth leaf
  placement, visible internal binding-level nodes and labels, outer-rim leaf
  labels, and faint label-extension links.
- Fixed Tree of life rendering so the same binding levels visible in Radial
  tree also appear in Tree of life, instead of labeling only leaves.
- Updated README, visualization docs, and current implementation architecture
  docs.
- Verified with `npm run build` in `vedenemo-ux`.
