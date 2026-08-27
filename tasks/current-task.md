# Current Task

## Add `LOCATION_LINE` and `LOCATION_AREA` Data Types

Status: executed.

### Goal

Extend Vedenemo's existing `LOCATION` support with two structured spatial data
types:

- `LOCATION_LINE`, an ordered sequence of `LOCATION` values representing a path
  or line.
- `LOCATION_AREA`, an ordered sequence of `LOCATION` values representing the
  closed outer boundary of a geographic area.

Both types are domain instance values, not visualization metadata and not model
entities.

### Scope

- Add `LOCATION_LINE` and `LOCATION_AREA` as built-in `DataType` values.
- Add pure-JDK normalized core instance value records for line and area values.
- Reuse existing `LOCATION` coordinate semantics for each contained point.
- Preserve exact point ordering.
- Validate that `LOCATION_LINE` contains at least two locations.
- Validate that `LOCATION_AREA` contains at least three distinct locations.
- Treat `LOCATION_AREA` as semantically closed without requiring or accepting a
  repeated final point.
- Support model attributes, `.vdos` model scripts, HTTP instance
  create/update/read responses, `.vdmp` export/import/precheck, and model
  consumer API descriptions.
- Support exact equality matching only.
- Keep ordered comparisons, string containment, generic array support, and
  `ValueSet` support out of scope for these spatial types.

### Out Of Scope

- Generic `ARRAY<T>` attribute support.
- Multi-polygons, disconnected lines, polygon holes, or interior rings.
- GIS projections, coordinate transformations, spatial indexing, spatial
  database behavior, point-in-polygon queries, distance calculation, line
  length calculation, or area calculation.
- GeoJSON or GPX import/export.
- Dedicated map, line, polygon, or spatial overlay visualization.
- `ValueSet` support for `LOCATION_LINE` or `LOCATION_AREA`.

### Acceptance Criteria

- `LOCATION_LINE` is available as a built-in attribute data type.
- `LOCATION_AREA` is available as a built-in attribute data type.
- Both types are based on ordered collections of existing `LOCATION` values.
- `LOCATION_LINE` preserves the exact ordering of its locations.
- `LOCATION_AREA` preserves the exact ordering of its boundary locations.
- `LOCATION_LINE` rejects values with fewer than two locations.
- `LOCATION_AREA` rejects values with fewer than three distinct locations.
- `LOCATION_AREA` has closed-boundary semantics without requiring or accepting a
  repeated final point in Vedenemo HTTP and `.vdmp` values.
- Invalid structures that cannot represent a line or area are rejected.
- Both types can be assigned to entity attributes like existing Vedenemo data
  types.
- HTTP instance create/update requests accept `LOCATION_LINE` values as objects
  with a `locations` array of `LOCATION` objects.
- HTTP instance create/update requests accept `LOCATION_AREA` values as objects
  with a `boundary` array of `LOCATION` objects.
- HTTP instance responses return both types as structured objects without loss
  of location data or ordering.
- `.vdmp` dump export/import preserves both types without loss of location data
  or ordering.
- `.vdos` model scripts can declare attributes with
  `dataType=LOCATION_LINE` and `dataType=LOCATION_AREA`.
- Exact equality filtering/querying works for both new data types.
- Ordered comparison and string containment operators reject both new data
  types.
- `ValueSet` creation rejects `LOCATION_LINE` and `LOCATION_AREA` types.
- Model consumers can distinguish `LOCATION`, `LOCATION_LINE`, and
  `LOCATION_AREA`.
- The implementation does not require generic array attribute support.
- The implementation does not introduce visualization or map-projection
  dependencies into the core spatial data types.

### Completion Notes

- Added `DataType.LOCATION_LINE` and `DataType.LOCATION_AREA`.
- Added pure-JDK `LocationLineValue` and `LocationAreaValue` records.
- Added instance normalization, equality matching, invalid structure rejection,
  and unsupported comparison rejection for the new spatial types.
- Added `.vdmp` export/import/precheck support for structured line and area
  values.
- Added `.vdos` model script declaration support through the existing
  `DataType` command/snapshot flow.
- Exposed structured HTTP request/response examples and model consumer
  metadata for the new types.
- Added terminal CLI, browser console, and HTTP session command datatype aliases
  for `location_line` / `location-line` and `location_area` / `location-area`.
- Updated frontend API-description handling so generated examples are
  structured objects and spatial query operators are equality-only.
- Updated README, CLI reference, model-instance dump format documentation,
  current implementation architecture docs, and backlog history.
- Verification passed with `mvn -q clean verify`,
  `cd vedenemo-ux && npm run build`, `git diff --check`, and README disclaimer
  preservation check.
