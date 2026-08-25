# Current Task

## Add retiredSince lifecycle metadata

Status: executed.

### Goal

Add optional `retiredSince` version metadata to versionable model items so it
can be populated and enforced by later work. `deprecatedSince` continues to
mean the item is deprecated but still usable. `retiredSince` records the version
from which an item should be considered prohibited from use once validation is
implemented.

### Scope

- Add nullable `retiredSince` metadata to versionable entities, attributes, and
  associations.
- Keep existing constructors and existing data compatible when the value is not
  present.
- Include `retiredSince` in HTTP lifecycle DTOs and console summaries.
- Include `retiredSince` in new `.vdos` snapshot exports.
- Allow `.vdos` imports without `retiredSince` to continue working.
- Do not add validation or enforcement for deprecated or retired item usage.

### Acceptance Criteria

- Existing model creation flows continue to create items with no retired
  version.
- Existing `.vdos` files that omit `retiredSince` can still be imported.
- New `.vdos` exports include `retiredSince=null` when no retired version is set.
- Entity, attribute, and association API responses include nullable
  `retiredSince`.
- Backend verification succeeds.

### Completion Notes

- Added `retiredSince` to `Versionable` and all concrete versionable model item
  types.
- Kept compatibility constructors for existing callers and test fixtures.
- Updated `.vdos` snapshot export/import, snapshot validation, HTTP responses,
  terminal CLI parsing, and browser-console summaries.
- Added a backlog item for future lifecycle usage enforcement instead of adding
  validation in this task.
