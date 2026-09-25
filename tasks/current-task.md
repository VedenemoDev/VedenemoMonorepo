# Current Task

## Phase 4: Harmonize visualization zoom interaction behavior

Status: executed

### Goal

Review and harmonize the shared zoom/scroll interaction across Hexbin-map, Tidy
tree, Radial tree, and Tree of life after all renderers use the shared viewport
shell.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Compare zoom increments, minimum and maximum zoom bounds, reset behavior,
  focus styling, scroll behavior, and accessibility labels across chart types.
- Align behavior where chart differences do not justify different UX.
- Document any intentionally chart-specific interaction differences in code
  comments only where the reason would otherwise be unclear.
- Keep interaction state runtime-only.

### Completion Notes

- Added shared zoom constants for the visualization viewport interaction.
- Harmonized the toolbar zoom step across Hexbin-map, Tidy tree, Radial tree,
  and Tree of life.
- Restored the tree chart lower zoom bound to 40% after follow-up review
  because it gives a useful structure overview for all tree renderers.
- Added consistent disabled states for zoom in, zoom out, and reset controls,
  including an explicit reset accessibility label.
- Kept Hexbin-map's 75% lower bound and deeper maximum zoom as intentional
  chart-specific behavior for geographic overlay inspection with D3 pan/scroll.
- Kept zoom and scroll state runtime-only.
- Verified with `npm run build` in `vedenemo-ux`.
