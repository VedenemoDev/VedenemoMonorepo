# Current Task

## Plan Entity data editor value-set dropdowns

Status: executed

### Goal

Improve the Entity data editor so attributes constrained by a fixed value set
are selected from the declared alternatives instead of being freely typed.

The concrete proof case is the `Tontti.vdos` model, where `Puu.laji` is a
required `TEXT` attribute bound to the `PuuLaji` value set.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Use existing API description metadata: `AttributeDescription.valueSetAzName`
  and `ApiDescriptionResponse.valueSets`.
- Render value-set-backed editor fields as dropdowns.
- Preserve existing free text, numeric, date/time, `DATA`, and `LOCATION`
  editor behavior for attributes without fixed value sets.
- Keep backend, model, CLI, `.vdos`, and `.vdmp` data unchanged.

### Completion Notes

- Added a frontend helper that resolves an attribute's referenced value set
  from the loaded API description.
- Rendered value-set-backed Entity data editor attributes as `<select>`
  controls with one option per value-set entry.
- Added save-time validation so a populated value-set-backed field must match
  one of the declared technical values.
- Preserved ordinary editor controls for attributes without a value set,
  including `DATA` textareas and `LOCATION` current-location support.
- Marked the backlog item executed and added the required
  `Planned vs. Executed Evaluation`.
- Verified with `npm run build` in `vedenemo-ux`.
