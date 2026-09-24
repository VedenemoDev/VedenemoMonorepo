# Current Task

## Extract shared visualization zoom viewport shell

Status: executed

### Goal

Create a small shared frontend-only viewport shell for SVG chart zoom controls
without changing the existing Hexbin-map behavior.

This task turns the already implemented Hexbin-map toolbar, focusable scroll
viewport, zoom scale display, and reset affordance into reusable browser UX
structure while leaving the Hexbin-map D3 projection and transform math owned
by `HexbinMapRenderer`.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Extract shared JSX/CSS for:
  - zoom in, zoom out, and reset controls;
  - zoom percentage display;
  - focusable scroll viewport;
  - shared accessibility labels and focus styling.
- Keep Hexbin-map rendering, D3 zoom behavior, projection math, scroll
  synchronization, legends, warnings, and data binding behavior unchanged.
- Keep all zoom and scroll state runtime-only.

### Out Of Scope

- Applying shared zoom behavior to non-Hexbin chart renderers.
- Rewriting D3 transform math into a generic engine.
- Backend, core, CLI, `.vdos`, or `.vdmp` changes.
- Persistent visualization settings.

### Acceptance Criteria

- Hexbin-map still supports toolbar zoom, wheel zoom, pinch-capable zoom,
  drag-pan, scrollbars, keyboard scrolling, and reset behavior as before.
- Shared viewport shell code is reusable by another SVG chart renderer without
  depending on Hexbin-map data types.
- Chart-specific D3 layout and transform logic remains inside the chart
  renderer or a chart-specific helper.
- Existing non-Hexbin visualization renderers continue to work.
- `cd vedenemo-ux && npm run build` succeeds after implementation.

### Completion Notes

- Added `VisualizationZoomViewport` in `vedenemo-ux/src/App.tsx` as a shared
  JSX shell for zoom toolbar controls, zoom percentage display, and a focusable
  scroll viewport.
- Updated `HexbinMapRenderer` to render its existing SVG through the shared
  shell while keeping all Hexbin-map D3 zoom behavior, projection math, scroll
  synchronization, warnings, and map-specific SVG classes in place.
- Renamed the reusable toolbar and viewport CSS from Hexbin-map-specific class
  names to shared `visualization-zoom-*` class names.
- Follow-up correction: removed the manual scroll offset reset that competed
  with the D3 identity transform so one Reset click returns Hexbin-map to 100%.
- Kept the implementation frontend-only and did not change backend, core, CLI,
  `.vdos`, `.vdmp`, model data, or persisted visualization configuration.
- Verified with `npm run build` in `vedenemo-ux`.
