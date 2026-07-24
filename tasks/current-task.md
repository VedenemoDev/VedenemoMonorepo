# Current Task

## Implement Directed Model Associations

Status: executed.

### Goal

Implement the first vertical slice of Vedenemo model-level associations while
preserving strict module boundaries.

### Scope

- Add pure model cardinality support.
- Add first-class directed ownership/reference associations under `ModelRoot`.
- Add core command execution, undo, and model journal support for association
  creation.
- Extend `.vdos` export/import and snapshot validation with additive
  association sections.
- Expose model-scoped and entity-scoped association views through HTTP.
- Extend shared console/terminal CLI inspection with `associations`.
- Add terminal CLI prompt flow for `assoc add`, `assoc add ownership`, and
  `assoc add reference`.
- Render ownership/reference associations as PlantUML edges in the UX.

### Completion Notes

- Added `Cardinality`, `Association`, `AssociationKind`,
  `OwnershipAssociation`, and `ReferenceAssociation` to `vedenemo-model-api`.
- `ModelRoot` now owns ordered model-level associations and validates
  association endpoints against existing entities.
- Added `CreateAssociationCommand`, `DeleteAssociationCommand`, undo result
  metadata, and model journal handling in `vedenemo-core`.
- `.vdos` now exports/imports `create-association` command lines and
  `association` snapshot lines. Older scripts without associations remain
  valid.
- Added HTTP endpoints:
  - `GET /models/{modelAzName}/associations`
  - `GET /models/{modelAzName}/entities/{entityAzName}/associations`
  - `POST /sessions/{uuid}/commands/create-association`
- Added shared association summaries and console association listing.
- Added terminal CLI association creation prompts.
- Updated UX PlantUML source generation to render ownership as `*--` and
  reference as `o--`.
- Updated README, CLI reference, implementation architecture documentation,
  backlog status, and persisted implementation plan.
- `mvn -B verify` passed.
- `npm run build` passed after rerunning with permission because Vite writes
  temporary files under `node_modules`.

### Next Steps

- Keep `Add True Bidirectional Relations` as the next backlog item.
- Implement relation only after this directed-association slice has been
  reviewed and exercised end to end.
