# Current Task

## Implement True Bidirectional Relations

Status: executed.

### Goal

Add first-class bidirectional `relation` support on top of the implemented
model-level association path while preserving strict module boundaries.

### Scope

- Add relation model objects as one association identity with two named ends.
- Preserve existing ownership/reference behavior and `.vdos` compatibility.
- Extend `CreateAssociationCommand`, undo, journal, `.vdos`, HTTP DTOs, shared
  console, terminal CLI, and UX PlantUML rendering for `relation`.
- Require each relation end to provide entity identifier, role name, and
  cardinality.
- Keep relation handling in pure model/core code and keep JSON/HTTP parsing in
  `vedenemo-web-api`.

### Completion Notes

- Added `RelationEnd` and `RelationAssociation` to `vedenemo-model-api`.
- Extended `AssociationKind` with `RELATION` and added end role/cardinality
  accessors to the association API.
- `CreateAssociationCommand` now supports relation-specific source and target
  role/cardinality fields while keeping directed ownership/reference commands
  compatible.
- `.vdos` export/import writes relation end fields only for `RELATION`
  associations and validates those fields during import.
- HTTP association DTOs and `POST /sessions/{uuid}/commands/create-association`
  now carry nullable relation end role/cardinality fields.
- Shared console and terminal CLI support `assoc add relation`; association
  listing shows relation roles and end cardinalities.
- UX PlantUML rendering now draws relations as solid undirected lines with role
  and cardinality labels on both ends.
- Updated README, CLI reference, implementation architecture documentation,
  backlog status, and persisted implementation plan.
- `mvn -B clean verify` passed.
- `npm run build` passed.

### Next Steps

- Review relation authoring and round-trip behavior through the Firebase UX.
- Continue with the next backlog item after relation behavior is accepted.
