# Current Task

## Add Hexbin-map zoom controls for LOCATION_AREA visualization

Status: executed

### Goal

Implement the planned browser UX improvement for `Hexbin-map` so a user can
zoom into a rendered `LOCATION_AREA` visualization when the default
fit-to-canvas view is too zoomed out for inspection.

The first executable slice should keep the initial fitted view, then allow the
user to zoom closer with visible controls, mouse-wheel zoom, and pinch zoom
where supported by the browser/SVG runtime.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Add runtime-only zoom state to the `Hexbin-map` renderer.
- Keep the default render fitted to the visualization area.
- Add visible zoom-out, zoom-in, and reset-to-fit controls.
- Support mouse-wheel and pinch zoom through the SVG map surface where
  supported.
- Keep the main boundary, subregion overlays, shared borders, and point
  overlays spatially aligned while zoomed.
- Keep legends, titles, warnings, setup controls, backend data, `.vdos`, and
  `.vdmp` formats unchanged.

### Out Of Scope

- Editing `LOCATION_AREA` geometry.
- Drawing new polygons or capturing new area data.
- Spatial measurement tools such as distance, perimeter, or area calculation.
- Persistent visualization configuration.
- Backend, core, CLI, `.vdos`, or `.vdmp` changes.
- A general zoom framework for all visualization types.

### Acceptance Criteria

- A rendered `Hexbin-map` visualization offers discoverable zoom-in, zoom-out,
  and reset-to-fit controls.
- Mouse-wheel and pinch zoom work on the map surface where supported by the
  target browser/SVG implementation.
- The initial view remains the existing fit-to-canvas map view.
- Zooming keeps the main boundary, subregion overlays, shared borders, and point
  overlays spatially aligned.
- Reset returns the visualization to the default fitted view.
- Zoom state is runtime-only and does not change model data, `.vdos`, `.vdmp`,
  or backend API behavior.
- Existing non-Hexbin visualization renderers continue to work.
- `cd vedenemo-ux && npm run build` succeeds.
- `mvn clean verify` succeeds.

### Completion Notes

- Added a compact `Hexbin-map` zoom toolbar with zoom-out, zoom percentage,
  zoom-in, and reset controls.
- Added D3 zoom behavior to the SVG map surface with scale limits, mouse-wheel
  zoom, drag-pan, and browser-supported touch/pinch zoom.
- Wrapped only the rendered map marks in a transformed zoom layer, keeping
  titles, details, legends, and warnings readable and unscaled.
- Kept the default render at the existing fit-to-canvas transform and reset
  returns to that fitted view.
- Left backend, core, CLI, `.vdos`, `.vdmp`, and persisted visualization
  configuration unchanged.
- `npm run build` succeeded in `vedenemo-ux`.
- `mvn clean verify` succeeded from the repository root.
