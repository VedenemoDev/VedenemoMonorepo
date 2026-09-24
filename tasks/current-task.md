# Current Task

## Implement scrollable zoom viewport for Hexbin-map LOCATION_AREA visualization

Status: executed

### Goal

Implement horizontal and vertical scrolling for the browser UX `Hexbin-map`
`LOCATION_AREA` renderer when the user zooms in enough that the rendered map is
larger than the visible map viewport.

The implementation should continue from the executed Hexbin-map zoom-controls
work and keep scrollbars, drag-pan, wheel zoom, pinch-capable zoom, toolbar
zoom, and reset behavior synchronized.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Add a constrained, focusable scroll viewport around the Hexbin-map SVG.
- Show horizontal and vertical scrollbars as needed when zoomed content
  overflows.
- Keep D3 zoom state synchronized with the scroll viewport.
- Preserve runtime-only zoom and scroll state.
- Keep all rendered map layers spatially aligned while zoomed and scrolled.

### Out Of Scope

- Editing `LOCATION_AREA` geometry.
- Drawing new polygons or capturing new area data.
- Spatial measurement tools.
- Persistent visualization configuration.
- Backend, core, CLI, `.vdos`, or `.vdmp` changes.
- A general scroll/zoom framework for all visualization types.

### Acceptance Criteria

- When a `Hexbin-map` `LOCATION_AREA` visualization is zoomed so the rendered
  map is larger than its visible viewport, horizontal and vertical scrollbars
  appear as needed.
- The user can scroll to hidden left/right and top/bottom portions of the
  zoomed map.
- Scrollbar movement stays synchronized with zoom controls, wheel zoom,
  pinch-capable zoom, drag-pan, and reset-to-fit behavior.
- Wheel or pinch zoom keeps the interaction focus near the pointer or gesture
  focal point where feasible, while toolbar zoom keeps the current viewport
  center stable.
- The scrollable map viewport can receive focus and supports standard keyboard
  scrolling behavior.
- Touch behavior allows pinch zoom where feasible without blocking ordinary page
  scrolling unnecessarily.
- The initial fitted map view remains unchanged and does not show unnecessary
  scrollbars.
- Reset returns the visualization to the default fitted view and clears or
  normalizes scroll position.
- Main boundary, subregion overlays, shared borders, and point overlays remain
  spatially aligned while zoomed and scrolled.
- Scroll and zoom state remains runtime-only and does not change model data,
  `.vdos`, `.vdmp`, or backend API behavior.
- Existing non-Hexbin visualization renderers continue to work.
- `cd vedenemo-ux && npm run build` succeeds after implementation.

### Completion Notes

- Implemented a focusable scroll viewport around the Hexbin-map SVG.
- The SVG surface now expands with the active zoom scale so native horizontal
  and vertical scrollbars appear when zoomed content overflows.
- D3 zoom transforms are synchronized with viewport scroll offsets, keeping
  scrollbar movement, wheel zoom, pinch-capable zoom, drag-pan, toolbar zoom,
  and reset behavior on one effective map view.
- Reset clears the scroll position and returns to the default fitted zoom.
- Added viewport focus styling for keyboard scrolling.
- Kept the change in `vedenemo-ux` only; backend, core, CLI, `.vdos`, `.vdmp`,
  model data, and persistence behavior were unchanged.
- Verified with `npm run build` in `vedenemo-ux`.
- Verified with `mvn clean verify` from the repository root.
