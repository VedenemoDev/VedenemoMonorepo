# Current Task

## Add current-location capture to LOCATION fields in Entity data editor

Status: executed

### Goal

Implement the first executable slice of the Entity data editor workflow where
each single-point `LOCATION` attribute can be filled from the browser's current
location.

For the Metsapalsta proof-of-concept, a user entering `Mittaus.lokaatio` should
be able to press `Use current location`, allow the browser location prompt, and
save a `LOCATION` value in the existing model-instance point shape:

```json
{ "latitude": 62.1234567, "longitude": 30.1234567 }
```

### Scope

- Keep the implementation in `vedenemo-ux`.
- Show the action only for attributes whose metadata has `dataType=LOCATION`.
- Request browser location only after the user presses `Use current location`.
- Fill only the selected `LOCATION` field.
- Save `LOCATION` values as structured `{ latitude, longitude }` objects.
- Preserve manual JSON entry for `LOCATION` fields.
- Show visible feedback for successful capture, unsupported browsers,
  permission denial, unavailable position, timeout, and unknown geolocation
  errors.

### Out Of Scope

- `LOCATION_LINE` route capture.
- `LOCATION_AREA` polygon capture or map drawing.
- Reverse geocoding addresses or place names.
- Persisting accuracy, altitude, heading, speed, timestamps, or browser
  permission state.
- Background tracking or continuous location watching.
- Backend, core model, `.vdos`, or `.vdmp` changes.

### Acceptance Criteria

- Every Entity data editor field for an attribute with `dataType=LOCATION`
  shows a `Use current location` button.
- Pressing the button requests current browser location and fills only that
  attribute field on success.
- The filled value can be saved through the existing entity create/update flow
  and is submitted as `{ "latitude": number, "longitude": number }`.
- Manual JSON entry for `LOCATION` values remains possible and is validated
  before submission.
- Geolocation failures produce a visible, understandable message and do not
  overwrite existing field content.
- Non-`LOCATION` attributes, `LOCATION_LINE`, and `LOCATION_AREA` fields are
  unchanged.
- `cd vedenemo-ux && npm run build` succeeds.

### Completion Notes

- Added `Use current location` controls beside Entity data editor fields whose
  attribute metadata is `LOCATION`.
- Used the browser Geolocation API only after the user presses the field action.
- Added per-field pending and feedback state for geolocation success/failure.
- Filled captured coordinates as compact JSON in the existing editable field.
- Added `LOCATION` form parsing so the editor submits a structured
  `{ latitude, longitude }` object to the existing create/update endpoints.
- Kept manual JSON entry available and validated latitude/longitude before
  submission.
- Left `LOCATION_LINE`, `LOCATION_AREA`, backend, core, CLI, `.vdos`, and
  `.vdmp` behavior unchanged.
- `npm run build` succeeded in `vedenemo-ux`.
