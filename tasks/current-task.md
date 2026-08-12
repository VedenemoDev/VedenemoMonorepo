# Current Task

## Family unit composite DATE model and loaders

Status: executed.

### Goal

Create a new `.vdos` model based on `FamilyUnitTreeComposite.vdos` that uses
the new `DATE` data type for modeled date fields, and add data loading scripts
for the British and Swedish royal-family datasets.

### Scope

- Keep the original `FamilyUnitTreeComposite.vdos` unchanged.
- Add a new model script with a distinct model `azName` and visible name.
- Keep the same entities, attributes, and associations as the source model.
- Change only date-meaning attributes from `TEXT` to `DATE`:
  - `Person.BirthDate`;
  - `Person.DeathDate`;
  - `FamilyUnit.StartDate`;
  - `FamilyUnit.EndDate`.
- Add British and Swedish data-loading scripts that target the new model.
- Preserve existing royal-family data values and links.
- Omit blank optional date values when creating records for the `DATE` model.

### Acceptance Criteria

- The new `.vdos` script imports successfully.
- Both new loader scripts can load their datasets against a local backend.
- Existing source model and loader scripts remain available unchanged.
- Verification demonstrates the new model accepts `DATE` attributes and the
  royal-family loaders avoid invalid blank date submissions.

### Completion Notes

- Added `.vedenemo/FamilyUnitTreeCompositeWithDates.vdos`.
- The new model keeps the original `Person`, `FamilyUnit`, and relation
  structure from `FamilyUnitTreeComposite.vdos`.
- Changed `BirthDate`, `DeathDate`, `StartDate`, and `EndDate` to `DATE`.
- Added British and Swedish royal-family loaders for the new model.
- The new loaders omit blank optional date values before POSTing instance data,
  because `DATE` does not accept blank strings.
- Verified direct `.vdos` import returns HTTP `201`.
- Verified both loaders create separate model instance roots and load all people,
  family units, and association links against a local backend.
