# Current Task

## Charles III family tree life-event sample coverage

Status: executed.

### Goal

Update the `Charles III Family Tree` test data so `LifeEvent` records are not
attached only to Charles. The sample should provide life events for several
people so relationship traversal and visualization bindings can exercise
non-Charles `Person_LifeEvents` paths.

### Scope

- Add non-Charles `LifeEvent` entries to
  `scripts/LoadFamilyTreeModelData.bash`.
- Reuse existing `Person`, `Place`, and `SourceRecord` sample data.
- Keep the loader idempotent through the existing `ensure_entity` and
  `ensure_link` behavior.
- Update the checked-in
  `.vedenemo/FamilyTree_Charles_III_Family_Tree_v1_0_0_2026_08_21.vdmp`
  dump to match the loader.
- Do not change the `FamilyTree` model definition.

### Acceptance Criteria

- `LIFE_EVENTS` in the loader includes events for Charles and other people.
- The checked-in `.vdmp` contains matching `LifeEvent` records.
- The checked-in `.vdmp` contains `Person_LifeEvents` links from multiple
  people, not only Charles.
- The dump remains valid JSON.

### Completion Notes

- Expanded the Charles III sample from 5 life events for Charles only to 19
  life events across 9 people.
- Added events for Elizabeth II, Philip, Diana, William, Harry, George,
  Charlotte, and Louis while preserving the existing Charles events.
- Updated the checked-in `.vdmp` with matching `LifeEvent`,
  `Person_LifeEvents`, `LifeEvent_Place`, and `LifeEvent_Sources` entries.
- Verified loader event distribution and dump link distribution with Python
  JSON checks.
