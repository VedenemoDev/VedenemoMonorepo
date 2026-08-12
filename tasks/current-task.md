# Current Task

## ISO date and time data types

Status: executed.

### Goal

Extend `DataType` with `DATE`, `TIME`, and `DATETIME` while preserving ISO
string storage/API/query values and browser-locale UX display.

### Scope

- Add `DATE`, `TIME`, and `DATETIME` to the pure JDK model API enum.
- Store and expose instance values for the new types as ISO strings.
- Validate submitted values for attributes declared with the new types:
  - `DATE`: ISO local date, `YYYY-MM-DD`;
  - `TIME`: ISO local time to second precision, `HH:mm:ss`;
  - `DATETIME`: ISO local datetime with minute or second precision.
- Support `=`, `<`, and `>` query comparisons for the new types, equivalent to
  `NUMERIC` operator availability.
- Keep `DATETIME` locale-neutral and timezone-free.
- Keep existing `.vdos` files unaffected unless they explicitly use one of the
  new data types with invalid values.
- Update CLI, command-console, web API, UX, examples, tests, and living
  implementation documentation as needed.

### Acceptance Criteria

- Attribute creation accepts `DATE`, `TIME`, and `DATETIME` through HTTP, CLI,
  command-console, and `.vdos` import paths.
- Instance create/update rejects invalid values for the new data types.
- Entity `_query` supports `=`, `<`, and `>` for the new data types.
- Existing data types and existing `.vdos` files continue to work unchanged.
- UX data entry uses date/time-aware controls where practical.
- UX display formats date/time values using the browser locale while API values
  remain ISO strings.
- `mvn clean verify` succeeds from the repository root.
- `cd vedenemo-ux && npm run build` succeeds.

### Completion Notes

- Added `DATE`, `TIME`, and `DATETIME` to `DataType`.
- Added pure JDK validation for ISO local date, time, and datetime values in
  runtime instance data.
- Kept new date/time values string-backed in API responses and instance
  records.
- Added `=`, `<`, and `>` query support for the new data types.
- Accepted the new data types through HTTP session commands, command-console,
  CLI normalization, and `.vdos` enum parsing.
- Updated UX input controls, query/filter operator handling, client-side
  validation, API examples, and browser-locale display formatting.
- Updated implementation and user documentation.
- Verified with `mvn -B clean verify`.
- Verified with `cd vedenemo-ux && npm run build`.
