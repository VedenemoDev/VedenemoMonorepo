# Current Task

## Prove shared zoom viewport with Tidy tree

Status: executed

### Goal

Apply the shared visualization zoom viewport shell to `TidyTreeRenderer` as the
first non-map proving case.

Tidy tree already uses a rectangular layout with natural horizontal and
vertical overflow, so this task verifies that the shared shell can support a
second SVG chart without hiding chart-specific layout and zoom behavior.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Add toolbar zoom, reset, and a focusable scroll viewport for Tidy tree.
- Preserve the current initial Tidy tree layout sizing and readable labels.
- Keep toolbar zoom centered on the current viewport where practical.
- Keep reset returning to the default unzoomed Tidy tree view and normalized
  scroll position.
- Keep Tidy tree zoom and scroll state runtime-only.

### Out Of Scope

- Changing the Tidy tree binding flow or hierarchy-building rules.
- Applying the shared viewport to Radial tree or Tree of life in this phase.
- A single generic D3 layout or transform implementation for all chart types.
- Backend, core, CLI, `.vdos`, or `.vdmp` changes.
- Persistent visualization settings.

### Acceptance Criteria

- Tidy tree has visible zoom in, zoom out, and reset controls.
- Tidy tree remains scrollable when the rendered or zoomed SVG exceeds the
  visible visualization region.
- Tidy tree labels, links, and nodes remain spatially aligned while zoomed.
- Reset restores the default Tidy tree scale and scroll position.
- Hexbin-map behavior remains unchanged.
- Radial tree and Tree of life continue to render as before.
- `cd vedenemo-ux && npm run build` succeeds after implementation.

### Completion Notes

- Updated `TidyTreeRenderer` to reuse `VisualizationZoomViewport` for the zoom
  toolbar, zoom percentage display, reset affordance, and focusable scroll
  viewport.
- Kept Tidy tree D3 hierarchy construction, layout sizing, link paths, node
  rendering, and label rendering inside `TidyTreeRenderer`.
- Added runtime-only Tidy tree zoom state that scales the rendered SVG
  dimensions so labels, links, and nodes remain aligned while scrollbars
  represent the zoomed content size.
- Added viewport-centered toolbar zoom where practical and reset behavior that
  restores 100% scale with normalized scroll position.
- Left Hexbin-map, Radial tree, Tree of life, backend, core, CLI, `.vdos`,
  `.vdmp`, model data, and persisted visualization configuration unchanged.
- Verified with `npm run build` in `vedenemo-ux`.
