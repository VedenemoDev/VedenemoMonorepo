# Current Task

## Plan Hexbin-map direct point attribute overlays

Status: executed

### Goal

Execute the Hexbin-map point overlay improvement so a selected associated
entity can provide point markers directly through one of its own `LOCATION`
attributes.

The concrete proof case is the `Tontti` model: after selecting
`Tontti_omistaa_Puu`, `Puu.lokaatio` should be selectable as the point
location without requiring a second point association.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Preserve existing Metsapalsta-style associated point paths such as
  `Puulaji -> Mittaus`.
- Keep the behavior generic and metadata-driven rather than hard-coding
  `Tontti`, `Puu`, or `lokaatio`.
- Reuse existing point marker rendering, legend, duplicate/conflict handling,
  outside-area diagnostics, and optional unlinked-point diagnostics.
- Keep true hexbin aggregation, backend query changes, model changes, and
  persistent visualization configuration out of scope.

### Completion Notes

- Added a direct point source option to the `Hexbin-map` point overlay binding
  when the selected point context entity has a `LOCATION` attribute.
- Updated point binding validation and resolution so the selected context
  entity itself can be the point entity.
- Preserved associated point traversal resolution for normalized paths such as
  `Puulaji -> Mittaus`.
- Kept rendering, CSS, backend, core, CLI, `.vdos`, and `.vdmp` unchanged.
- Marked the backlog item executed and added the required
  `Planned vs. Executed Evaluation`.
- Verified with `npm run build` in `vedenemo-ux`.
- Verified with `mvn clean verify` from the repository root.
