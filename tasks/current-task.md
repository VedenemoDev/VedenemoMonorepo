# Current Task

## Add parent association selection to entity instance creation

Status: executed

### Goal

Implement the first executable slice of the Entity data editor workflow where a
user can create a new entity instance and optionally link it to an existing
parent entity instance in the same save action.

For the Metsapalsta proof-of-concept, a user should be able to create a new
`Mittaus` instance with its relevant data and select the correct `Puulaji`
parent through the `Puulaji -> Mittaus` association before saving.

### Scope

- Keep the implementation browser UX first unless current APIs are
  insufficient.
- Show parent-link controls only during create/copy flows for entities with
  eligible incoming associations.
- Discover eligible parent links from associations whose target entity is the
  entity being created.
- Let the user choose no parent link even when a parent association is
  available.
- Auto-select the only eligible parent association as a convenience, while
  still allowing the user to clear it.
- Load parent instance candidates from the selected model-instance root and
  selected parent entity.
- Create the child instance first, then create the selected parent-to-child
  association link.
- Keep a successfully created child visible if association-link creation fails,
  and show a clear retryable error.
- Mark the backlog item executed after verification while leaving it in
  `tasks/backlog.md` as history.

### Out Of Scope

- Backend transaction semantics.
- New core association semantics.
- Durable persistence changes.
- Full Entity data editor redesign.
- Inline parent creation.
- Multiple simultaneous parent links in the first slice.
- `.vdos` or `.vdmp` format changes.

### Acceptance Criteria

- The Entity data editor can create `Mittaus` data and link it to a selected
  `Puulaji` parent in one create flow when the model exposes that association.
- Parent-link controls are derived from eligible incoming associations rather
  than hard-coded Metsapalsta names.
- The user can choose no parent link where the model/editor allows unlinked
  creation.
- The save button communicates when a create-and-link action will run.
- If child creation succeeds but link creation fails, the created child remains
  loaded and the link failure is visible.
- `cd vedenemo-ux && npm run build` succeeds.
- `mvn clean verify` succeeds from the repository root.

### Completion Notes

- Added parent-link controls to the Entity data editor create/copy flow when
  the selected entity has eligible incoming associations.
- Derived parent-link options generically from model metadata by finding
  associations whose target entity is the entity being created.
- Auto-selected the only eligible parent association while preserving the
  explicit `No parent link` option.
- Loaded parent instance candidates from the selected model-instance root and
  used existing instance labeling for readable selector values.
- Chained save behavior so the child instance is created first and the selected
  parent-to-child association link is created second.
- Kept a created child loaded if parent-link creation fails, and surfaced the
  link failure in both the editor status and parent-link section.
- Kept implementation in `vedenemo-ux`; no backend, core, CLI, `.vdos`, or
  `.vdmp` changes were needed.
- `npm run build` succeeded in `vedenemo-ux`.
- `mvn clean verify` succeeded from the repository root.
