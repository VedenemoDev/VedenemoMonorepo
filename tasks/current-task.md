# Current Task

## Draw Hexbin-map subregion borders above main boundary

Status: executed

### Goal

Adjust `Hexbin-map` SVG layering so subregion border colors visually override
the main region boundary wherever subregion borders overlap it.

The rendered map should make linked subregion boundaries clearer by ensuring
subregion strokes are not hidden or visually weakened by the main region
outline.

### Scope

- Keep this feature in `vedenemo-ux`.
- Change only SVG layer order and any directly related CSS stroke styling.
- Preserve the existing style assignment modes and legend behavior.
- Update visualization documentation when implemented.

### Out Of Scope

- Shared-border dual-color rendering between adjacent subregions.
- Geometry matching or topology analysis.
- Backend model-rule changes.
- New backend endpoints.
- Persistent visualization configuration.

### Acceptance Criteria

- Subregion border colors appear above the main region boundary where the
  geometries overlap.
- The main region extent remains readable when subregions are present.
- Existing no-overlay single-boundary rendering remains usable.
- Existing automatic fill, automatic border-only, manual border-only, and
  manual pattern/color modes remain usable.
- Frontend build succeeds.

### Completion Notes

- Moved the main `Hexbin-map` boundary stroke below the subregion polygon
  overlay layer while keeping the main fill below both.
- Preserved the existing point markers, labels, overlay notices, and legend
  layer order above the map geometry.
- Kept no-overlay single-boundary rendering on the same boundary stroke path.
