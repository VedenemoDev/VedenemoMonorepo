# Association Implementation Plan

Status: executed.

This plan persists the association implementation sequence so work can resume
cleanly in a new Codex session.

## Step 1: Cardinality Value Object

Status: completed.

- Add a pure JDK cardinality value object in `vedenemo-model-api`.
- Support textual forms `1`, `0..1`, `0..*`, `1..*`, bounded ranges such as
  `2..5`, and shorthand `*` as `0..*`.
- Normalize `1..1` to `1`.
- Reject malformed values, negative bounds, upper bounds lower than lower
  bounds, and `0..0`.
- Preserve stable text output for future `.vdos`, API, CLI, and diagram use.
- Add focused model-api tests.

## Step 2: Directed Association Model/Core Slice

Status: completed.

- Add first-class model-level associations owned by `ModelRoot`.
- Introduce sealed association types for ownership/reference now, with relation
  reserved for the later bidirectional step.
- Validate source/target entity existence and model-wide association `azName`
  uniqueness.
- Add core command execution, undo, and model journal recording.
- Keep all model/core code pure JDK and explicitly wired.

## Step 3: `.vdos` Persistence Slice

Status: completed.

- Add `create-association` command import/export.
- Add `association` snapshot sections.
- Keep existing `.vdos` files containing only entities and value attributes
  valid.
- Reconstruct any entity-level navigation references from model-root
  association definitions during import.
- Add parser/export/snapshot validation tests.

## Step 4: API, Shared Console, And CLI Slice

Status: completed.

- Add HTTP DTOs/endpoints in `vedenemo-web-api`.
- Provide model-scoped and entity-scoped association views.
- Extend shared Java console behavior with `assoc add ownership`,
  `assoc add reference`, prompt-driven `assoc add`, and `associations`.
- Keep terminal CLI as a thin HTTP/file-I/O client.
- Keep browser `/console` backed by shared Java command behavior.

## Step 5: UX And PlantUML Slice

Status: completed.

- Render ownership/reference as PlantUML edges.
- Use composition-style black diamond for ownership.
- Use aggregation-style hollow diamond for reference.
- Do not add arrowheads for directed ownership/reference in the first version.
- Show association edges in the browser model view; no separate association
  list/table is required initially.

## Step 6: Bidirectional Relation Slice

Status: completed.

- Add true bidirectional `relation` after directed associations work end to end.
- Model a relation as one identity with two named ends.
- Give each end an entity identifier, role name, and cardinality.
- Prevent drift into two independent references.
- Add command, undo, journal, `.vdos`, HTTP, CLI/console, UX, diagram, and test
  coverage.
