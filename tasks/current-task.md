# Current Task

## Phase 3: Extend shared zoom viewport to radial tree charts

Status: executed

### Goal

Apply the shared visualization zoom viewport shell to `RadialTreeRenderer` and
`TreeOfLifeRenderer` while preserving each chart's radial layout behavior.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Add shared toolbar zoom, reset, and focusable scroll viewport behavior to
  Radial tree.
- Add shared toolbar zoom, reset, and focusable scroll viewport behavior to
  Tree of life.
- Keep radial label rotation, root centering, node/link alignment, and color
  behavior intact.
- Keep zoom and scroll state runtime-only.

### Out Of Scope

- Changing tree binding, filtering, traversal, or aggregation behavior.
- Changing radial/tree-of-life layout algorithms beyond what is necessary for
  viewport integration.
- Backend, core, CLI, `.vdos`, or `.vdmp` changes.
- Persistent visualization settings.

### Completion Notes

- Added the shared visualization zoom viewport shell to Radial tree and Tree of
  life renderers.
- Extracted Tidy tree's runtime SVG zoom and scroll bookkeeping into a shared
  local hook used by all three SVG tree renderers.
- Kept circular coordinate systems, label orientation, node/link alignment,
  guide/extension paths, and color behavior in the existing D3 renderers.
- Reset restores the default 100% rendered view and normalized scroll position.
- Verified with `npm run build` in `vedenemo-ux`.
