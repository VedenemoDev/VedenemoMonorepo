# Current Task

## Plan Hexbin-map lower zoom floor and legend reachability

Status: executed

### Goal

Improve the Hexbin-map visualization viewport so dense point legends remain
reachable and the map can zoom out below the previous 75% floor.

The concrete proof case is loading `Tontti.vdos` with `RitosentieRandom.vdmp`
or equivalent `Ritosentie` tree data, using `Puu.lokaatio` point markers and
the generated point legend.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Lower the Hexbin-map minimum zoom percentage slightly below 75%.
- Keep Hexbin-map zoom and scroll behavior runtime-only.
- Make tall subregion or point legends part of the scrollable visualization
  content so they are not clipped outside the SVG viewport.
- Keep backend, model, CLI, `.vdos`, and `.vdmp` data unchanged.

### Completion Notes

- Lowered the Hexbin-map minimum zoom from 75% to 55%, adding one more useful
  toolbar zoom-out step.
- Sized the Hexbin-map SVG canvas from the larger of the map body, subregion
  legend, and point legend heights.
- Moved titles and legends into the same zoomed content layer as the map, so
  zooming out scales the legend with the rest of the visualization instead of
  clipping it at the fixed SVG edge.
- Preserved existing point rendering, legend entries, warnings, D3 zoom, drag
  pan, scroll synchronization, and reset controls.
- Marked the backlog item executed and added the required
  `Planned vs. Executed Evaluation`.
- Verified with `npm run build` in `vedenemo-ux`.
