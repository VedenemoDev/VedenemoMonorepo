# Current Task

## Add Hexbin-map dual-color shared subregion borders

Status: executed

### Goal

Improve `Hexbin-map` subregion readability by rendering shared borders between
adjacent subregions with visual contribution from both neighboring subregion
styles.

When two linked subregion polygons share the same boundary segment, the map
should make it clear that the segment belongs to both subregions instead of
letting one subregion's stroke visually dominate the other.

### Scope

- Keep this feature in `vedenemo-ux`.
- Keep all geometry matching and SVG rendering frontend-only.
- Prefer exact shared-segment matching for the first implementation.
- Support reversed coordinate order when matching exact shared segments.
- Document any tolerance or partial-overlap limitations if they remain.

### Out Of Scope

- Backend geometry libraries.
- New backend endpoints.
- Persistent visualization configuration.
- Spatial topology validation.
- Automatic gap closing or polygon repair.
- Full tolerance-based line conflation unless exact matching proves
  insufficient for the current data.
- Partial-overlap splitting unless needed by concrete data.

### Acceptance Criteria

- Subregion borders still render correctly when subregion polygons do not share
  exact boundary segments.
- Exact shared boundary segments between two subregions are detected even when
  the segment point order is reversed.
- Shared boundary segments render with both neighboring subregion colors.
- The shared-border overlay does not obscure the main region boundary or
  unrelated non-shared subregion borders.
- Existing no-overlay, automatic fill, automatic border-only, manual
  border-only, and manual pattern/color modes remain usable.
- Frontend build succeeds.

### Completion Notes

- Added frontend-only exact shared-segment detection for `Hexbin-map`
  subregion boundaries using normalized source coordinate endpoint pairs.
- Treated reversed point order as the same shared segment and ignored explicit
  duplicated polygon closing points for segment detection.
- Rendered shared borders in a dedicated SVG overlay layer above ordinary
  subregion polygons with two thin parallel strokes, one per neighboring
  subregion color.
- Preserved ordinary subregion rendering for non-shared edges and all existing
  Hexbin-map style assignment modes.
- The first implementation intentionally does not split partial overlaps or
  conflate near-identical/tolerance-based segments.
