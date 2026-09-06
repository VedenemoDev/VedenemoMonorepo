# Current Task

## Add skeletal Hexbin-map wizard root selection path

Status: executed

### Goal

Implement the first baby step for a browser UX `Hexbin-map` visualization
wizard path using <https://observablehq.com/@d3/hexbin-map> as the D3 reference
point.

The wizard path should be available for a loaded model only when the model has
at least one entity with a `LOCATION_AREA` attribute. It should let the user
select one valid root-scoped model-instance element, select one valid
`LOCATION_AREA` attribute from that element, and press `Visualize` to render
the selected boundary as a plain SVG map outline.

### Scope

- Add the `Hexbin-map` chart type to the existing browser visualization wizard.
- Keep the implementation in `vedenemo-ux`.
- Keep D3 usage frontend-only.
- Detect model-level chart eligibility from root-scoped API metadata.
- Load root-scoped candidate instances for entities that have `LOCATION_AREA`
  attributes.
- Keep root selection single.
- Keep attribute selection single.
- Use the current Metsapalsta dump shape as the first supported
  `LOCATION_AREA` input:

```json
{
  "boundary": [
    { "latitude": 61.845, "longitude": 24.288 },
    { "latitude": 61.851, "longitude": 24.288 },
    { "latitude": 61.851, "longitude": 24.29085 }
  ]
}
```

- Show root items and attributes with empty, missing, or unparsable
  `LOCATION_AREA` data as disabled with a concise `No LOCATION_AREA data`
  reason.
- Render the selected `LOCATION_AREA` boundary as plain SVG before introducing
  actual hexbin cells.
- Update README, visualization documentation, current implementation
  architecture documentation, and backlog status.

### Out Of Scope

- Backend model-rule changes.
- New backend endpoints.
- New `.vdos` or `.vdmp` syntax.
- Persistent visualization configuration.
- Additional `LOCATION_AREA` input formats.
- Data-edit UX support for spatial values.
- Association traversal from the selected root to child areas.
- Metsapalsta plus linked Metsakuvio overlay layers.
- Classification coloring.
- Numeric metrics.
- Point-density hexbins from `LOCATION` attributes.
- Hex-cell generation, clipping, or intersection logic.

### Acceptance Criteria

- `Hexbin-map` appears as a visualization chart type for models that have at
  least one `LOCATION_AREA` attribute.
- `Hexbin-map` is disabled with a reason for models that do not have
  `LOCATION_AREA` attributes.
- The Hexbin-map binding panel lists root-scoped instances whose entity has a
  `LOCATION_AREA` attribute.
- Root items with no usable `LOCATION_AREA` value remain visible but disabled
  with `No LOCATION_AREA data`.
- After selecting a root item, the attribute selector lists that entity's
  `LOCATION_AREA` attributes.
- Attributes with empty, missing, or unparsable `LOCATION_AREA` values remain
  visible but disabled with `No LOCATION_AREA data`.
- `Visualize` is enabled only after one valid root item and one valid
  `LOCATION_AREA` attribute are selected.
- Pressing `Visualize` fetches the selected root item and renders the selected
  boundary as a plain SVG map outline.
- Existing tree visualizations continue to build.
- Frontend build succeeds.
- Backend Maven verification succeeds.

### Completion Notes

- Added `Hexbin-map` to the frontend chart-type registry.
- Added `LOCATION_AREA` chart eligibility and root-scoped candidate loading.
- Added a dedicated Hexbin-map binding panel with single root and single
  attribute selection.
- Added disabled no-data behavior for unusable root items and attributes.
- Added a parser for the current Metsapalsta `LOCATION_AREA` dump shape.
- Added a D3-backed SVG renderer that projects the selected latitude/longitude
  boundary into a fitted plain SVG outline.
- Kept D3/map code in `vedenemo-ux`; no backend or core dependencies were
  added.
- Marked the backlog item executed while keeping it in `tasks/backlog.md` as
  history.
