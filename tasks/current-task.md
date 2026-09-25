# Current Task

## Allow Hexbin-map zoom below 100%

Status: executed

### Goal

Let Hexbin-map zoom out modestly below its default 100% view without changing
the shared visualization zoom viewport shell or disrupting existing map
inspection behavior.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Lower the Hexbin-map D3 zoom minimum below 100%.
- Preserve existing zoom in, reset, drag-pan, wheel/pinch-capable zoom, and
  scroll synchronization behavior.
- Keep zoom and scroll state runtime-only.

### Out Of Scope

- Changing Hexbin-map projection, boundary rendering, legend rendering, data
  binding, or warnings.
- Changing Tidy tree, Radial tree, or Tree of life behavior.
- Backend, core, CLI, `.vdos`, or `.vdmp` changes.
- Persistent visualization settings.

### Completion Notes

- Changed Hexbin-map D3 `scaleExtent` from a 100% minimum to a 75% minimum.
- Removed the Hexbin-map SVG fixed CSS minimum width so the rendered SVG can
  visually follow modest below-100% zoom instead of being forced back toward
  the old minimum layout width.
- Left reset behavior anchored at D3 identity, so Reset still restores the
  default 100% map view.
- Verified with `npm run build` in `vedenemo-ux`.
