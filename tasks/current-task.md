# Current Task

## Add Hexbin-map associated point overlays with path-based styling

Status: executed

### Goal

Implement the first executable slice of the planned `Hexbin-map` point overlay
workflow.

For the Metsapalsta example, the visualization should be able to render
`Mittaus.lokaatio` point markers through the explicit path:

```text
Metsapalsta -> Metsakuvio -> Puulaji -> Mittaus
```

The user should select visual roles in the binding wizard rather than relying
on hard-coded entity names. `Metsakuvio.alue` remains the subregion area,
`Puulaji.nimi` can provide point classification/style, and
`Mittaus.lokaatio` provides point coordinates.

### Scope

- Extend the browser `Hexbin-map` binding with optional point overlay roles.
- Resolve point markers through explicit association paths below the selected
  subregion overlay.
- Style points by a selected attribute on the retained style-context entity.
- Render raw point markers over the existing area and subregion layers.
- Use simple marker shapes and colors with a point legend.
- Warn about missing point locations, points outside their associated
  subregion, conflicting duplicate paths, and optionally geometrically
  contained points that are not linked through the selected path.
- Keep the implementation frontend-only unless current API payloads are
  insufficient.
- Mark the backlog item executed after verification while leaving it in
  `tasks/backlog.md` as history.

### Out Of Scope

- True hexbin aggregation.
- Moving `Puulaji.nimi` onto `Mittaus`.
- Backend query language changes.
- Core model, CLI, `.vdos`, or HTTP API changes.
- Persistent visualization configuration.
- GIS-grade topology, clipping, or projection behavior.

### Acceptance Criteria

- Hexbin-map binding asks for visual roles rather than hard-coded
  Metsapalsta-specific names.
- The Metsapalsta role chain can be represented as subregion area
  `Metsakuvio.alue`, style context `Puulaji`, style attribute `Puulaji.nimi`,
  point entity `Mittaus`, and point location `Mittaus.lokaatio`.
- Rendered points use raw markers over the existing area/subregion map.
- Point colors and shapes are deterministic and reflected in a legend.
- Conflicting duplicate paths render with a neutral/conflict marker style and
  produce warnings.
- Points outside their associated subregion produce warnings.
- Optional unlinked-point diagnostics are disabled by default and produce
  warnings only.
- `cd vedenemo-ux && npm run build` succeeds.
- `mvn clean verify` succeeds from the repository root.

### Completion Notes

- Added optional Hexbin-map point overlay binding fields for point style
  context association, point association, point `LOCATION` attribute, point
  style attribute, point legend label template, and unlinked-point diagnostics.
- Kept visual roles generic while supporting the concrete
  `Metsakuvio -> Puulaji -> Mittaus` path.
- Rendered associated point markers with deterministic color and shape
  assignment plus a point legend.
- Added data warnings for missing point locations, outside-subregion points,
  conflicting duplicate style/subregion paths, and optional unlinked points
  geometrically inside a rendered subregion.
- Kept all changes in `vedenemo-ux`; no backend, core, CLI, `.vdos`, or HTTP
  API changes were needed.
- `npm run build` succeeded in `vedenemo-ux`.
- `mvn clean verify` succeeded from the repository root.
