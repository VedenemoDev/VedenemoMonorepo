# Current Task

## Add new LOCATION datatype

Status: executed.

### Goal

Add a built-in `LOCATION` attribute data type for WGS 84 geographic point
coordinates while preserving core purity and keeping map/visualization behavior
out of the datatype itself.

### Scope

- Add `LOCATION` to the supported `DataType` values.
- Represent normalized runtime location values with a dedicated pure-JDK record.
- Accept structured HTTP and `.vdmp` values with numeric `latitude` and
  `longitude` fields.
- Validate required, finite WGS 84 latitude and longitude ranges.
- Support exact equality matching for `LOCATION` values.
- Reject ordered comparison, string containment, string-shaped coordinates,
  missing coordinates, non-numeric coordinates, and non-finite coordinates.
- Allow `.vdos` model scripts to declare attributes with `dataType=LOCATION`.
- Keep map, spatial search, route/track, altitude, timestamp, and UX helper
  behavior out of this task.

### Acceptance Criteria

- `LOCATION` is available as a built-in Vedenemo attribute data type.
- A model entity can define an attribute whose type is `LOCATION`.
- `.vdos` model scripts can declare attributes with `dataType=LOCATION`.
- A normalized `LOCATION` instance value is represented in core by a dedicated
  pure-JDK record.
- HTTP instance create/update requests accept `LOCATION` values only as
  structured objects with numeric `latitude` and `longitude` fields.
- HTTP instance responses return `LOCATION` values as structured objects with
  `latitude` and `longitude` fields.
- `.vdmp` dump export/import preserves normalized `LOCATION` values without
  intentional rounding or truncation.
- Latitude and longitude are interpreted as WGS 84 decimal-degree coordinates.
- Latitude and longitude ranges are validated.
- Missing latitude or longitude values are rejected.
- Non-numeric, non-finite, or string-shaped location values are rejected using
  the normal Vedenemo validation/error mechanism.
- Exact equality filtering/querying works for `LOCATION` values.
- Ordered comparison and string containment operators reject `LOCATION`
  attributes.
- The data type itself does not depend on any map or visualization
  implementation.
- Backend verification succeeds.

### Completion Notes

- Added `DataType.LOCATION` and pure-JDK `LocationValue`.
- Added core instance normalization, finite/range validation, and exact
  equality matching for structured location values.
- Kept ordered comparison and string containment unsupported for `LOCATION`.
- Updated HTTP responses to emit structured `{latitude, longitude}` objects.
- Updated `.vdmp` precheck/import/export support for location values.
- Updated `.vdos` script tests and datatype parsing paths for CLI, browser
  console, and HTTP session commands.
- Updated README, CLI reference, current implementation architecture docs, and
  backlog history.
