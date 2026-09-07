# Current Task

## Add Hexbin-map subregion style assignment modes

Status: executed

### Goal

Extend the browser UX `Hexbin-map` binding phase with additional subregion
overlay style assignment modes beyond the original automatic patterned overlay
mode.

Users can choose between automatic filled overlays, automatic border-only
overlays, manual border-only overlays, and manual fill pattern/color
assignments for linked renderable subregions.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Keep style assignments runtime-only.
- Reuse the existing linked subregion preview data and legend placeholder
  validation.
- Reuse the existing SVG overlay rendering path.
- Keep backend model rules, API endpoints, and `.vdos` / `.vdmp` syntax
  unchanged.
- Update README, visualization documentation, current implementation
  architecture documentation, backlog status, and session record.

### Out Of Scope

- Persistent visualization configuration.
- Backend model-rule changes.
- New backend endpoints.
- New `.vdos` or `.vdmp` syntax.
- Attribute-grouped or classification-based styling.
- Numeric metric-driven styling.
- Spatial clipping, topology validation, or true hex-cell generation.

### Acceptance Criteria

- The `Hexbin-map` binding phase offers the existing automatic filled overlay
  behavior and the new border-only and manual style assignment modes.
- Automatic border-only mode renders linked subregions with transparent fills
  and deterministic, visually distinct stroke colors.
- Manual border-only mode renders linked subregions with transparent fills and
  the user-selected stroke color for each rendered subregion.
- Manual fill-pattern mode requires the user to select a color/pattern pair for
  each rendered subregion and uses those selections in both the map and legend.
- Manual assignment rows are keyed to stable linked subregion identities so
  selections do not shift unexpectedly during re-rendering.
- Validation prevents rendering when a renderable subregion lacks required
  manual style settings.
- Existing automatic filled overlay rendering remains usable.
- Existing no-overlay single-boundary Hexbin-map rendering remains usable.
- Frontend build succeeds.

### Completion Notes

- Replaced the single automatic Hexbin-map overlay style mode with four
  runtime style modes: automatic pattern/color, automatic border color, manual
  border color, and manual fill pattern/color.
- Added manual assignment rows for renderable linked subregions, keyed by
  stable subregion instance id and labeled through the existing legend template.
- Added validation that blocks manual rendering until every renderable
  subregion has the required color and, for filled manual mode, pattern.
- Updated SVG map and legend rendering so border-only modes use transparent
  fills while preserving matching subregion strokes.
- Preserved existing no-overlay and automatic filled overlay behavior.
