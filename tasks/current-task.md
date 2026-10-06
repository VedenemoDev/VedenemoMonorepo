# Current Task

## Add Hexbin-map point location correction mode

Status: executed

### Goal

Let users correct GPS-inaccurate `LOCATION` point overlay data directly from the
rendered `Hexbin-map` visualization through an explicit edit mode.

### Scope

- Keep the implementation frontend-only in `vedenemo-ux`.
- Keep normal `Hexbin-map` viewing read-only by default.
- Add an explicit point edit mode for maps with point overlays.
- Let users select one rendered point, preview local metric D-pad nudges, reset,
  cancel, or save.
- Save only the selected point instance's chosen `LOCATION` value through the
  existing entity-instance update API.
- Patch the current rendered map after save so the corrected point appears
  immediately.
- Leave backend, core, CLI, `.vdos`, and `.vdmp` formats unchanged.

### Completion Notes

- Added editable source metadata to `HexbinMapPoint` for the point entity,
  instance, and selected `LOCATION` attribute.
- Added an explicit `Edit points` toggle in the rendered `Hexbin-map` viewer.
- Point markers remain view-only by default; in edit mode they can be selected
  by pointer or keyboard.
- Added a compact point correction panel with source details, original and
  adjusted coordinates, projection context, nudge step selection, D-pad controls,
  `Reset`, `Cancel`, and `Save`.
- D-pad nudging uses local meters-per-degree conversion and previews the marker
  in the active map projection.
- Saving submits the full selected instance value map through the existing
  `updateEntityInstance` helper with only the chosen `LOCATION` value replaced.
- Successful saves patch the current `HexbinMapData` in memory and show a
  process-local persistence reminder.
- Updated visualization documentation.
- Verified with `npm run build` in `vedenemo-ux`.
