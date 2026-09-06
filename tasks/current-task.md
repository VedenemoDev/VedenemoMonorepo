# Current Task

## Add Hexbin-map subregion overlay layers

Status: executed

### Goal

Extend the browser UX `Hexbin-map` visualization so a selected main
`LOCATION_AREA` region can render linked subregion `LOCATION_AREA` instances as
overlay polygons with deterministic pattern/color styles and a matching legend.

The concrete motivating case is `Metsapalsta3.vdmp`: render `Metsapalsta.alue`
as the parent boundary and associated `Metsakuvio.alue` values as distinct
subregion overlays.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Reuse existing root-scoped instance query, entity-instance fetch, and
  association-link endpoints.
- Keep D3, SVG patterns, overlay styling, legend rendering, and wizard state
  frontend-only.
- Extend the existing `Hexbin-map` binding with one optional subregion overlay
  association.
- Offer only overlay associations whose related entity has at least one
  `LOCATION_AREA` attribute.
- Offer only `LOCATION_AREA` attributes for the selected subregion entity.
- Add automatic per-subregion style assignment with deterministic pattern/color
  combinations.
- Add a runtime-only legend label template using `{attribute}` placeholders and
  `{id}`.
- Keep existing single-boundary rendering usable when no overlay association is
  selected.
- Update README, visualization documentation, current implementation
  architecture documentation, backlog status, and session record.

### Out Of Scope

- Backend model-rule changes.
- New backend endpoints.
- New `.vdos` or `.vdmp` syntax.
- Persistent visualization configuration.
- Manual per-subregion style table.
- Attribute-grouped or classification-based subregion styling.
- Linked `Puulaji` classification coloring.
- Numeric metrics.
- Point-density hexbins from `LOCATION` attributes.
- Actual hex-cell generation, clipping, or intersection logic.
- Additional `LOCATION_AREA` input formats.

### Acceptance Criteria

- `Hexbin-map` can render one selected main `LOCATION_AREA` boundary together
  with all valid linked subregion `LOCATION_AREA` boundaries.
- The binding panel offers eligible subregion associations and subregion area
  attributes only.
- The Metsapalsta case can select `Metsapalsta.alue` as the extent and
  `Metsakuvio.alue` as the overlay layer.
- Each rendered subregion has a distinct deterministic pattern/color style and
  a matching border, until available combinations are exhausted.
- The UX previews how many linked subregions are renderable and how many lack
  usable `LOCATION_AREA` data.
- A legend appears with style swatches matching the map overlays.
- The legend text uses a user-editable template with `{attribute}` references
  and `{id}` support.
- Invalid legend placeholders prevent rendering with a clear validation
  message.
- Existing single-boundary Hexbin-map rendering remains usable when no overlay
  association is selected.
- Existing tree visualizations continue to build.
- Frontend build succeeds.

### Completion Notes

- Extended `Hexbin-map` binding state with optional overlay association,
  subregion boundary attribute, automatic style mode, and legend label
  template.
- Added eligible overlay traversal discovery for associated entities that have
  `LOCATION_AREA` attributes.
- Added linked subregion preview loading through existing association-link and
  entity-instance APIs.
- Added deterministic automatic per-subregion style assignment by sorted linked
  instance id.
- Added D3/SVG rendering for patterned subregion polygons, matching borders,
  parent boundary redraw, overlay notices, and legend swatches.
- Preserved no-overlay single-boundary rendering.
- Updated README, visualization documentation, and current implementation
  architecture documentation.
