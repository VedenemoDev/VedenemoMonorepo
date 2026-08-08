# Current Task

## Model instance data visualization proof-of-concept

Status: executed.

### Goal

Implement the first runtime-only UX proof of concept for visualizing model
instance data through user-selected bindings between Vedenemo model elements and
chart-specific visual concepts.

The proof path uses `AlbumCollectionSimple`, loaded from
`.vedenemo/LevykokoelmaSimple.vdos`, and renders its model instance data as a
D3 Tidy tree.

### Scope

- Add `Visualize...` to model-instance root actions in the `Model instances`
  tab.
- Open `/visualizeWizard?modelAzName={modelAzName}&instanceRootId={instanceRootId}`
  in a new browser tab.
- Implement a `/visualizeWizard` route with three phases:
  - chart type selection;
  - model element binding;
  - visualization.
- Add D3 as a frontend dependency only.
- Add actual multi-chart extension points in `vedenemo-ux`, with `Tidy tree` as
  the first implemented chart type.
- Show invalid chart types disabled with an explanation.
- Let the Tidy tree binding support outgoing and incoming association traversal.
- Prevent cyclic entity paths in the binding.
- Support label templates such as `{Name}` and `{Name} ({year})`.
- Render real root-scoped model instance data and include a refresh control.
- Keep visualization bindings runtime-only; do not persist templates or binding
  state.

### Acceptance Criteria

- A user can open `Visualize...` from a model-instance root and reach
  `/visualizeWizard` in a new browser tab with the selected route parameters.
- The chart type selection page shows `Tidy tree`, disabled with a reason if it
  is not eligible for the selected model.
- The implementation has a clear chart-type extension point, not only
  single-purpose route code.
- The binding page supports chart root label, entity levels, association
  direction, and label templates.
- The visualization page renders a D3 Tidy tree from real model instance data.
- The `AlbumCollectionSimple` proof case can render root `Mikan levykokoelma`,
  artist nodes, and album nodes under each artist through
  `Artistilla_on_albumeja`.
- The visualization page can refresh data without losing the current runtime
  binding.
- `npm run build` succeeds in `vedenemo-ux`.

### Completion Notes

- Added a `Visualize...` action to model-instance root menus.
- Added `/visualizeWizard` as a three-step runtime-only UX route.
- Added D3 as a frontend-only dependency and kept backend/core modules
  unchanged.
- Added a chart-type registry extension point with `Tidy tree` as the first
  registered chart.
- Implemented Tidy tree eligibility, binding validation, outgoing/incoming
  traversal selection, cyclic entity-path prevention, label templates, data
  refresh, and scrollable SVG rendering.
- Updated `docs/architecture_doc.md` and kept the executed backlog item in
  `tasks/backlog.md` as history.
