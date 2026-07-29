# Backlog

## Plan Dynamic Model Instance Data API

Status: planning.

### Goal

Plan the first process-local model-instance capability: users should be able to
create and query data records whose shape is validated against a selected
Vedenemo model definition.

In this phase, model definitions act like database schemas and model instances
act like rows/data stored according to those schemas. The implementation should
not persist model-instance data yet. It should introduce a generic in-memory
instance model and a dynamic HTTP API whose available entity entry points and
accepted fields are derived from the loaded model definition at runtime.

### Initial Scope

- Add a pure Vedenemo-owned runtime instance model for data created from a
  `ModelRoot`.
- Keep instance storage process-local and in memory only.
- Bind instance data to a loaded model by model `azName` and the model version
  visible when the instance dataset is created.
- Support creating entity instances for modeled entities.
- Support querying entity instances for modeled entities.
- Support creating association/relation instance links for modeled
  associations.
- Support relationship-aware queries, for example querying albums through their
  related artist instance.
- Validate incoming instance values against the selected model schema:
  - model `azName` must resolve to a loaded `ModelRoot`;
  - entity `azName` must exist in that model;
  - submitted attribute `azName` values must exist on that entity;
  - unknown attribute names should be rejected;
  - values should be validated according to `DataType`.
- Validate association links against the selected model schema:
  - association `azName` must exist in that model;
  - source and target instance ids must exist;
  - source and target instances must belong to the association's source and
    target entity types.
- Add a dynamic HTTP API description endpoint for a selected model.
- Keep JSON parsing/serialization and HTTP routing in `vedenemo-web-api`.
- Keep core/model instance validation independent of Javalin, Jackson, and web
  DTO classes.
- Do not add database persistence, authentication, generated source code,
  WebSockets, or schema migration in the first slice.

### Recommended First Architecture Direction

Introduce the model-instance concepts in a Vedenemo-owned backend module with
pure JDK dependencies, likely `vedenemo-model-api` for immutable value objects
and `vedenemo-core` for registry/service behavior if command/session semantics
are needed. If the implementation grows enough to justify a boundary, a later
`vedenemo-instance-api` module can be introduced, but the first planning pass
should avoid adding a module only for naming neatness.

Likely pure model/core types:

- `InstanceId`: backend-assigned UUID string for one entity instance.
- `EntityInstance`: one data record for one model entity.
- `InstanceValue`: typed value wrapper for attribute values.
- `AssociationInstanceLink`: one runtime relationship link between source and
  target entity instances for a modeled association.
- `ModelInstanceDataset`: all in-memory instance data for one model `azName`
  and schema version, including entity instances and association links.
- `ModelInstanceRegistry`: process-local registry keyed by model `azName`,
  then entity `azName`, then `InstanceId`, plus association links keyed by
  association `azName`.
- `ModelInstanceService`: validates requests against `ModelRegistry` /
  `ModelRoot` and reads/writes `ModelInstanceRegistry`.

The web API should expose a static resource class, for example
`InstanceDataResource`, rather than dynamically registering and unregistering
Javalin routes per model. Dynamic behavior should come from resolving
`modelAzName` and `entityAzName` path parameters against the current
`ModelRegistry`.

Recommended endpoint shape:

- `GET /data/{modelAzName}/_api`
  - Returns a JSON description of the dynamic instance API for the selected
    model.
  - Includes entity `azName`/`visName`, attribute definitions, data types, and
    concrete create/list/query/read URLs.
- `POST /data/{modelAzName}/{entityAzName}`
  - Creates one entity instance.
  - Request body contains attribute values keyed by attribute `azName`.
  - Response includes generated instance id, entity `azName`, model `azName`,
    and stored values.
- `GET /data/{modelAzName}/{entityAzName}`
  - Lists stored instances for one entity, with deterministic order and a
    conservative default limit.
  - Supports simple exact-match attribute filters in query parameters.
- `GET /data/{modelAzName}/{entityAzName}/{instanceId}`
  - Reads one stored instance.
- `POST /data/{modelAzName}/{entityAzName}/_query`
  - Queries stored instances with a JSON query body. This supports more
    expressive queries, including relationship predicates.
- `POST /data/{modelAzName}/_links/{associationAzName}`
  - Creates one association/relation instance link between source and target
    entity instances.
- `GET /data/{modelAzName}/_links/{associationAzName}`
  - Lists links for one modeled association.

The `_api`, `_query`, and `_links` segments are intentionally
underscore-prefixed. Current `azName` rules require names to start with an ASCII
letter, so underscore segments can be reserved for dynamic API control
endpoints without conflicting with model/entity/attribute/association `azName`
values.

The first slice should be HTTP-only. Terminal and browser CLI commands remain
focused on model building and maintenance/troubleshooting unless later usage
shows that direct data access commands are needed.

### Dynamic API Description

Do not use a WSDL-style query parameter in the first slice. Prefer a normal JSON
metadata endpoint:

```text
GET /data/{modelAzName}/_api
```

This endpoint can be Vedenemo-specific and small at first. It should not pretend
to be OpenAPI unless the generated description is actually valid OpenAPI. A
later task can add an OpenAPI projection if external tooling needs it.

Initial response should describe:

- selected model `azName`, `visName`, and version;
- each entity `azName` and `visName`;
- each entity attribute `azName`, `visName`, and `DataType`;
- each association `azName`, `visName`, kind, endpoint entity types,
  cardinality, and role metadata where available;
- supported operations for that entity in this runtime;
- supported link operations for each association in this runtime;
- JSON body examples or field maps sufficient for a client to build forms and
  requests dynamically.

### Instance Data Semantics

First-slice instance data should be generic and schema-driven:

- Instances are not Java classes generated per model entity.
- Attribute values are stored in a map keyed by attribute `azName`.
- Instance ids are backend-assigned UUID strings, not user-visible `azName`
  values and not monotonic row numbers.
- Attribute order in responses should follow the model entity attribute order
  where practical.
- Missing attributes should be allowed because Vedenemo does not yet model
  required/optional field constraints.
- Unknown attributes should be rejected.
- Duplicate values, uniqueness constraints, indexes, and transactions are out
  of scope.

Suggested first `DataType` handling:

- `TEXT`: JSON string.
- `NUMERIC`: JSON number or numeric string parsed to a deterministic JDK value
  such as `BigDecimal`.
- `URL`: JSON string accepted only if it is a strict absolute URL.
- `DATA`: JSON string in the first slice; binary uploads and structured data
  schemas are deferred.

Both query styles should be available:

- simple exact-match filters on `GET /data/{modelAzName}/{entityAzName}`;
- richer JSON query bodies on
  `POST /data/{modelAzName}/{entityAzName}/_query`.

### Association Instance Semantics

Associations are the hardest part and should be planned explicitly rather than
accidentally encoded as scalar fields.

First implementation slice:

- Implement scalar entity-instance create/list/read/query.
- Include association definitions in `/data/{modelAzName}/_api` so clients can
  see available relationship semantics.
- Store relationship links separately from entity attribute maps.
- Key relationship links by association `azName`.
- A link should reference source and target `InstanceId` values and validate
  that those ids belong to the association's source and target entity types.
- Support relationship-aware entity queries so clients can express conditions
  through modeled associations, for example "albums whose artist is this artist
  instance".
- Ownership/reference/relation semantics must not be flattened into ordinary
  attributes.
- Cardinality enforcement should be planned explicitly; it should not be
  implied by the scalar-attribute storage model.

Possible traversal endpoint:

- `GET /data/{modelAzName}/{entityAzName}/{instanceId}/{associationAzName}`

### Naming And `azName` Direction

Keep the current `azName` rule in the first dynamic API slice:

- starts with an ASCII letter;
- then contains only ASCII letters, ASCII digits, and underscores;
- preserves original casing;
- compares case-insensitively where uniqueness is enforced.

Hyphenated path segments are more common in public REST APIs, but changing
`azName` rules now would require a broader migration through commands, `.vdos`,
HTTP DTOs, CLI prompts, diagrams, tests, and existing snapshots. Current
underscore-based `azName` values are valid URL path segments and already fit the
implemented model/schema semantics.

If later public API ergonomics need hyphenated URLs, add a separate alias or URL
projection layer instead of changing the canonical model identifier first.

### Runtime Binding Between Model And Instances

The instance registry should not copy the whole `ModelRoot` into every
instance. It should store:

- canonical model `azName`;
- model version at dataset creation time;
- entity `azName` per collection/instance;
- attribute values keyed by attribute `azName`;
- generated instance ids.

Validation should resolve the current loaded `ModelRoot` from `ModelRegistry`
for each write/query operation.

The first implementation should ignore schema-version mismatches. It still
stores the model version visible at dataset creation time, but it does not block
reads, writes, or queries when the currently loaded model version differs.
Real schema migration strategies and compatibility rules are explicitly deferred
to future planning.

Instance operations should not broadcast existing model-change events. Runtime
data changes should get a separate event channel later if live data views need
push notifications.

### Static API Separation

Keep existing model-authoring APIs under `/models` and `/sessions`.

Put dynamic model-instance data under a distinct top-level prefix such as
`/data`. This prevents dynamic entity names from colliding with static
authoring endpoints and makes the runtime boundary obvious:

- `/models/...`: model definition/schema authoring and inspection.
- `/sessions/...`: command/session editing behavior.
- `/console/...`: browser virtual CLI sessions.
- `/data/...`: runtime model-instance data derived from loaded schemas.

### Testing Scope

First implementation should include deterministic tests for:

- API description for a model with entities and attributes;
- API description for associations and available link operations;
- create instance with valid values;
- create association link with valid source and target instances;
- reject unknown model `azName`;
- reject unknown entity `azName`;
- reject unknown association `azName`;
- reject unknown attribute `azName`;
- reject invalid `DataType` value;
- reject invalid association link endpoints;
- list/read created instances in deterministic order;
- query by one or more attribute values;
- query through an association, for example albums by artist;
- prove state is process-local/in-memory and does not require external
  services.

### Resolved Decisions

- Include association/relation instance links in the first API design. The
  scalar-only plan is not enough because ordinary models need relationship
  queries, such as finding albums through their artist.
- Ignore schema-version mismatches in the first implementation. Record schema
  version metadata, but defer migration and compatibility rules.
- Use UUID strings for `InstanceId`.
- Validate `URL` values as strict absolute URLs.
- Support both simple exact-match GET filters and richer POST query bodies.
- Do not reuse model-change events for instance data changes. Plan a separate
  runtime-data event channel later.
- Keep the first slice HTTP-only; CLI commands can remain focused on model
  building and maintenance/troubleshooting.

### Future Design Items

- Define schema migration and compatibility rules for instance data after model
  definitions change.
- Design a separate runtime-data event channel if UX or API clients need live
  instance-data updates.
- Decide whether direct CLI data-access commands are ever needed, or whether
  data access should remain HTTP-only.

### Open Questions

1. Should association-link creation be only through dedicated `_links`
   endpoints, or should entity instance create/update requests also be able to
   create links inline?
2. What exact JSON shape should relationship-aware `_query` requests use for
   association predicates?
3. Should first-slice association links enforce cardinality immediately, or
   should they only validate source/target entity types until cardinality rules
   are planned in more detail?
4. Should UUID instance ids be globally unique across all models/entities in
   the registry, or is uniqueness within each model/entity collection enough?

## Plan Association Semantics For Vedenemo Models

Status: executed.

### Goal

Define the first coherent relationship model for Vedenemo without jumping
straight to full UML associations.

### Current Direction

Use three user-facing semantic categories:

- `ownership`: a directed association where the source conceptually owns the
  target in model-instance semantics.
- `reference`: a directed association where source and target have independent
  lifecycles.
- `relation`: a bidirectional association with one identity and two named ends.

The preferred implementation direction is to introduce a separate model-level
association object, not to model associations as attributes. The first
implementation should still be narrow: support directed associations for
`ownership` and `reference`, then add true bidirectional `relation` support
after the model-level association path is proven end to end.

### Resolved Decisions

- Ownership at the model/metamodel level records ownership intent only. It does
  not directly enforce lifecycle behavior in the model object itself, because
  lifecycle behavior applies to model instances created from the metamodel.
- Entity references inside association ends may use target entity `azName` for
  the current phase. A separate stable entity id or handle is not required yet.
- Associations live directly under `ModelRoot`.
- Association insertion order should be natural creation order. Since
  associations are added after entities, creation can validate that referenced
  entities exist.
- Later entity modification/deletion restrictions should respect association
  integrity: an entity referenced by an association cannot be removed before the
  association end is released or the association is removed.
- Command wording should use:
  - `assoc add ownership`
  - `assoc add reference`
  - `assoc add relation`
- `.vdos` should use current naming style:
  - command section: `create-association`
  - snapshot section: `association`
- PlantUML rendering should distinguish association kinds immediately:
  - `ownership`: black diamond at the owner/source end.
  - `reference`: hollow diamond at the referring/source end.
  - `relation`: solid line between entities.
- Directed ownership/reference diagrams do not need arrowheads because the
  diamond marks which end is the source/referrer/owner.
- Do not add alias commands in the first version. Use only canonical association
  command keywords at first, and add aliases later only if usage experience
  justifies them.

### Rationale

Vedenemo currently has a complete vertical slice for entities and attributes:

- pure model objects in `vedenemo-model-api`;
- command execution and undo in `vedenemo-core`;
- `.vdos` command/snapshot export and import;
- HTTP endpoints and DTOs in `vedenemo-web-api`;
- shared terminal/browser console command behavior;
- terminal CLI and UX rendering paths.

Introducing associations as model-owned elements requires more code than adding
reference attributes, but it keeps relationships from being forced into an
attribute-shaped model. It gives associations their own identity, maps naturally
to diagrams as arrows or lines with labels, and leaves a cleaner path toward
bidirectional relations and later association classes.

The first slice should still move through the existing vertical path:

- pure model objects in `vedenemo-model-api`;
- command execution and undo in `vedenemo-core`;
- `.vdos` command/snapshot export and import;
- HTTP endpoints and DTOs in `vedenemo-web-api`;
- shared terminal/browser console command behavior;
- terminal CLI and UX rendering paths.

### Alternatives Considered

- Add directed reference attributes first.
  - Benefit: smaller first implementation because entities already own
    attributes and the current command/API/CLI flow already creates and lists
    attributes.
  - Cost: risks making relationships subordinate to entity fields when the
    intended model semantics may need association identity, diagram edges,
    bidirectionality, and future association classes.

- Treat associations only as normal attributes with a `REFERENCE` data type.
  - Benefit: small implementation change.
  - Cost: weak semantics; target entity, cardinality, ownership, and future
    bidirectionality would become bolted-on metadata instead of explicit model
    concepts.

- Generate inverse references automatically for bidirectional relationships.
  - Benefit: convenient authoring.
  - Cost: risks two definitions drifting unless there is a single relation
    identity behind both ends.

## Add Cardinality Value Object

Status: executed.

### Goal

Introduce a pure JDK cardinality/multiplicity model that can be reused by
directed model-level associations and later bidirectional relations.

### Scope

- Add a Vedenemo-owned cardinality value object in the model layer.
- Support UML-like forms:
  - `1`
  - `0..1`
  - `0..*`
  - `1..*`
  - bounded ranges such as `2..5`
- Validate lower and upper bounds deterministically.
- Preserve a stable textual representation for `.vdos`, API responses, CLI
  output, and diagrams.

### Pondering

The likely model is a small immutable value object rather than an enum, because
bounded ranges cannot be represented well by a fixed enum. The wildcard upper
bound should be explicit in code rather than represented by an arbitrary magic
number.

### Resolved Decisions

- Accept shorthand `*` as `0..*`.
- Normalize `1..1` to `1`.
- Default blank interactive prompt input to `1`.
- Reject `0..0` cardinality as meaningless for associations.

## Add Directed Model Associations

Status: executed.

### Goal

Add the first implemented association capability as separate model-level
association objects from one entity to another.

### Scope

- Add a model-level association collection owned by `ModelRoot`.
- A directed association should include:
  - `azName`
  - `visName`
  - source entity identifier
  - target entity identifier
  - source role name if needed
  - target role name or label
  - cardinality
  - kind: `OWNERSHIP` or `REFERENCE`
  - lifecycle version metadata
- Preserve current value attribute behavior for existing `DataType` attributes;
  value attributes remain entity-owned attributes.
- Keep association `azName` uniqueness rules clear, likely model-wide and
  case-insensitive like entity names.
- Add command execution, undo, model journal recording, and tests.
- Extend `.vdos` export/import and snapshot validation.
- Extend HTTP DTOs and endpoints without leaking JSON/web types into core.
- Extend shared console behavior and terminal CLI prompts.
- Keep browser `/console` using shared Java command behavior.

### Resolved Decisions

- Implement `Association` as a sealed interface.
- Use concrete sealed subtype names:
  - `OwnershipAssociation`
  - `ReferenceAssociation`
  - `RelationAssociation`
- Start with a single `associations` command for listing associations.
- Support source and target entity selection by both latest entity-list number
  and exact `azName`, mirroring existing attach/entity selection behavior.
- Support both explicit kind-specific commands and a prompt-driven generic
  command:
  - `assoc add ownership`
  - `assoc add reference`
  - `assoc add relation`
  - `assoc add`, which prompts for kind separately
- Existing `.vdos` files containing only entities and value attributes remain
  valid after association support is added. Association sections are additive,
  not mandatory.
- Ask for association `azName` as the last association-creation prompt.
- Build a unique `azName` suggestion from the referenced entities and other
  association data. The user can accept the suggestion, edit it, or enter a
  fully manual unique `azName`.
- Directed-association `azName` suggestions should use source entity plus
  `visName`, falling back to source entity plus target entity when `visName`
  cannot produce a valid suggestion. Do not include association kind in the
  suggestion unless needed to avoid a uniqueness conflict.
- Directed associations do not require separate role names in the first
  version. `visName` is enough and may be used as the diagram, API, and CLI
  label. Role names are deferred.
- Association `azName` values share one model-wide namespace across all
  association kinds in the first implementation.

### Pondering

`ownership` and `reference` should probably share one internal directed
association structure initially, with a small kind enum distinguishing lifecycle
semantics. That keeps the first implementation smaller while still recording
the user's intent and keeping relationships as first-class model elements.

The implementation should avoid enforcing cascade delete or containment
lifecycle rules until user-visible delete/edit commands are available. Until
then, `ownership` can be a model semantic and rendering/API signal rather than a
runtime deletion rule.

Associations should be rendered in diagrams as lines with labels, rather than
as pseudo-fields inside entity boxes. Entity-local projections can be
introduced later if generated APIs or UI views need field-like navigation.

## Expose Directed Associations In API, UX, And Diagrams

Status: executed.

### Goal

Make directed model-level associations visible and usable across existing HTTP,
console, CLI, UX, and PlantUML rendering surfaces.

### Scope

- Return model-level associations from API endpoints with source entity, target
  entity, role/label, cardinality, kind, and lifecycle fields.
- Render `ownership` and `reference` differently enough in PlantUML to
  communicate intent.
- Update the browser model view so association edges are visible without
  hand-authored diagrams.
- Update CLI and web console output so users can inspect directed associations.
- Add focused tests for DTO shape and rendered textual output.

### Resolved Decisions

- The first UX rendering only needs PlantUML diagram edges. No separate
  inspectable association list/table is required in the initial UX.
- PlantUML should render:
  - ownership as composition with a black diamond.
  - reference as aggregation-like with a hollow diamond.
  - relation as a solid line.
- Directed ownership/reference rendering does not need arrowheads in the first
  version.
- Add both model-scoped and entity-scoped association API views in the first API
  slice:
  - model-scoped: list all associations for a model.
  - entity-scoped: list associations touching one entity.
- The CLI `associations` command should list associations related to the
  selected entity when an entity is selected, and all model associations when no
  entity is selected.
- Association list output must state its context in the title, because the
  prompt alone is not explicit enough.
- Entity-scoped API responses should return one ordered list with explicit
  source and target fields. Do not split incoming/outgoing groups in the first
  version.

### Pondering

The API should likely use separate association endpoints or a model-detail
response section rather than extending attribute responses with optional
association fields. That keeps value attributes and associations distinct in the
client contract.

The earlier model-scoped versus entity-scoped API question means:

- model-scoped API: list all associations for a model, for example
  `GET /models/{modelAzName}/associations`;
- entity-scoped API: list only associations touching one entity, for example
  `GET /models/{modelAzName}/entities/{entityAzName}/associations`.

The first implementation should include both model-scoped and entity-scoped
views. `ModelRoot` remains the ownership boundary, while entity-scoped views
are convenience projections for CLI, UX, generated APIs, and focused
inspection.

## Add True Bidirectional Relations

Status: executed.

### Goal

Introduce `relation` as a first-class bidirectional model-level association
only after directed model-level associations are implemented and validated end
to end.

### Scope

- Model a relation as one identity with two named ends.
- Each relation end should include:
  - entity identifier
  - role name
  - cardinality
- Prevent the two ends from drifting into contradictory independent references.
- Add command execution, undo, model journal recording, `.vdos` support, HTTP
  DTOs, CLI/console flows, tests, and diagram rendering.

### Completion Notes

- Added `RelationAssociation` and `RelationEnd` as pure model-api types.
- Extended association command execution and undo through the existing
  model-level association path.
- Extended `.vdos` command and snapshot lines with relation-only source/target
  role and cardinality fields.
- Extended HTTP DTOs, terminal CLI prompts, shared console listing, and UX
  PlantUML rendering for `relation`.
- Kept relation as one association identity; no inverse reference pair is
  generated.
- Verified with `mvn -B clean verify` and `npm run build`.

### Resolved Decisions

- Relation names are required. Role names are reserved for relation ends.
- Relation objects live directly under `ModelRoot`.
- Participating entities may also hold direct references to associations that
  touch them for navigation efficiency. This is an implementation convenience;
  `ModelRoot` remains the ownership boundary.
- First-version relations should keep both ends navigable at model/code level.
- Participating entities should store association object references at code
  level. `.vdos` snapshots should store only association `azName` references,
  not object references.
- All association kinds should share the same model-wide `azName` namespace in
  the first implementation.
- Relation end role names are not identifier data and do not need model-wide
  uniqueness. When role names are introduced later, different ends of the same
  relation are expected to have different roles, but this is a clarity rule
  rather than relation identity.
- `.vdos` stores association definitions in command and snapshot sections.
  Participating-entity association references are reconstructed from those
  definitions during import, after entities exist and integrity checks pass.
- Association classes are expected to be useful later, but they are explicitly
  out of scope for the first association/relation implementation.

### Pondering

`relation` should not merely generate two unrelated reference attributes. The
main value of a relation is that Vedenemo can understand that both navigable
ends describe one modeled relationship.

Many-to-many relationships with data should probably be modeled as an explicit
entity in the first implementation, for example `Enrollment` between `Student`
and `Course`, rather than adding arbitrary attributes to relations.

The earlier non-navigable-end question was about model/code semantics, not only
visual rendering. A non-navigable end would mean the relation exists in the
model but generated APIs or instance navigation would not expose traversal from
that end. First-version relations keep both ends navigable and postpone
non-navigable ends.

The earlier relation identity question was about whether a relation needs its
own stable `azName`/identifier in addition to its end role names. The current
direction is: yes, relation names are required, so `.vdos` and future
persistence should identify a relation by its own association/relation `azName`.
Role names identify the ends, not the relation itself.

## Make CLI Commands Case-Insensitive And Add Console Input History

Status: executed.

### Goal

Improve normal and virtual CLI console ergonomics without changing command
parameters.

### Scope

- Treat command words case-insensitively in both terminal `VedenemoCli` and the
  browser `/console`.
- Keep parameters case-sensitive; do not normalize model, entity, file, or other
  argument casing.
- Keep terminal and browser console command input history only for the current
  console session.
- Support previous-command navigation with Arrow Up and Ctrl+P.
- Support next-command navigation with Arrow Down and Ctrl+N.

### Completion Notes

- Updated shared console and terminal CLI command dispatch to normalize command
  words only.
- Tightened model/entity `azName` parameter matching to remain case-sensitive.
- Added in-session terminal CLI command input history for real TTY use.
- Added in-session browser console input history in React state.
- Added focused Java tests and updated user-facing docs.

## Add Terminal CLI Snapshot Listing For Local Vedenemo Scripts

Status: executed.

### Goal

Make the real terminal CLI easier to use with local `.vdos` files stored under
a `.vedenemo` directory.

### Scope

- Add `snapshots` to list `.vdos` files from the CLI working directory's
  `.vedenemo` subdirectory.
- Keep `load <path>` working as before.
- Allow `load <number>` to load from the latest `snapshots` list.
- Prefer `.vedenemo/<name>.vdos` for bare relative `load <name>` when that file
  exists.
- Keep browser `/console` behavior unchanged because it has no local file
  access.

### Completion Notes

- Added deterministic, numeric `.vedenemo/*.vdos` snapshot listing in
  `vedenemo-cli`.
- Added snapshot-number and `.vedenemo` default lookup support for terminal
  `load`.
- Added focused CLI tests and updated CLI/architecture documentation.

## Keep Browser Console Input Focused And Cancellable

Status: executed.

### Goal

Reduce friction in the virtual CLI by keeping keyboard focus on the command
prompt instead of requiring an extra click after each command, and make Esc a
clear cancel shortcut for prompt/input work.

### Scope

- Focus the `/console` command input after the browser console session starts.
- Restore focus to the command input after asynchronous command execution
  finishes.
- Let incidental clicks inside the console surface return focus to the command
  input when the console is ready.
- Clear virtual console command input when Esc is pressed.
- Cancel terminal CLI interactive prompt flows when Esc is pressed, returning
  to the normal CLI prompt without executing the partially entered operation.
- Expose the shortcut in CLI/browser-console text.

### Completion Notes

- Added a React input ref and focus effect in `vedenemo-ux/src/App.tsx`.
- Console surface clicks now return focus to the command input when enabled.
- Added terminal prompt cancellation handling in `VedenemoCliApp`.
- Documented the Esc shortcut in CLI docs and UX console text.
- Verified with `mvn -B -pl vedenemo-cli -am test` and `npm run build`.

## Move Backend Ping From UX Button To CLI Console Command

Status: executed.

### Goal

Remove the main UX Ping button and expose backend connectivity checks through
the CLI command surface instead.

### Scope

- Remove the Ping button, status text, and related fetch/state helpers from
  `vedenemo-ux`.
- Add a shared `ping` command to `vedenemo-command-console` so both terminal
  `VedenemoCli` and the `/console` virtual CLI can use it.
- Keep the existing `GET /models/ping` endpoint available as the backend health
  endpoint used by the command.
- Update CLI/help documentation and focused tests.

### Completion Notes

- Added `ModelClient.ping()` and shared `ConsoleSession` handling for `ping`.
- Terminal CLI `ping` calls `GET /models/ping` through `HttpModelClient`.
- Web console `ping` runs through the in-process console adapter and returns
  the same user-facing OK message.
- Removed the main-page UX Ping button and related state/styles.
- Added focused shared-console, terminal CLI, and web console endpoint tests.

## Create ModelRoot Entity And Model Registry

Status: executed. Full task text retained here for history.

### Goal

Introduce the first concrete model root entity and expose a minimal API for
adding and listing currently known models.

This task intentionally moves beyond the original ping-only web API. Keep the
change narrow: no durable persistence, no parser, no model internals beyond the
root metadata, and no UX changes unless explicitly added later.

### Domain Model

Add a `ModelRoot` entity with these fields:

- `azName`: ASCII model identifier. Must be unique within the currently loaded
  model registry using case-insensitive comparison. Preserve the original case
  as entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the model. UTF-8 string. Must be non-blank.
  Uniqueness is not enforced.
- `version`: semantic version in `major.minor.patch` format. `major`, `minor`,
  and `patch` are non-negative integers. Leading zeroes are normalized away, so
  `01.002.3` becomes `1.2.3`.

Implementation constraints:

- Put Vedenemo-owned model types in a pure JDK module, most likely
  `vedenemo-model-api`, unless implementation analysis finds a better existing
  boundary.
- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak Javalin, JSON-library, or HTTP types into model/core/SPI modules.

### Model Registry

Add a process-local registry of currently loaded / known models.

Preferred interpretation of "application global":

- a single registry instance wired by the application composition root for the
  running process
- explicit constructor wiring
- no dependency injection framework
- avoid hidden static mutable global state unless explicitly approved

The registry must enforce `azName` uniqueness.

Uniqueness is case-insensitive, so `Example` and `example` conflict, but the
original submitted case is preserved for display and API responses.

### HTTP API

Add two endpoints in `vedenemo-web-api`:

- `POST /models/add`
  - Adds a new `ModelRoot` to the current process registry.
  - Request body should contain `azName`, `visName`, and `version`.
  - Response should include the created model root.
  - Duplicate `azName` and invalid input must return a client error response.

- `GET /models/list`
  - Returns all currently known / loaded model roots.
  - Response order should be deterministic.

Keep HTTP parsing/serialization concerns inside `vedenemo-web-api`.

### Tests

Add command-line runnable tests for both endpoints.

Minimum coverage:

- adding a valid model succeeds
- listing models includes the added model
- duplicate `azName` is rejected
- case-only duplicate `azName` is rejected
- blank `visName` is rejected
- invalid version is rejected
- version leading zeroes are normalized

The tests must run through the existing backend GitHub Actions workflow via:

```bash
mvn -B clean verify
```

### Architecture Documentation

After successful implementation and test execution, update
`docs/architecture_doc.md` in the same change.

The update should document only the concrete implementation that exists after
the task is complete:

- `ModelRoot`
- model registry
- new HTTP endpoints
- relevant runtime flow
- any new dependencies or test infrastructure

### Test Dependencies

It is acceptable to introduce JUnit 5 and a small HTTP client or HTTP testing
dependency for endpoint tests, scoped to test modules only.

### Planning Status

All planning questions are resolved. This task is ready to move to execution
when selected as the current task.

## Refactor ModelStorage To Use ModelRoot

Status: executed. Full task text retained here for history.

### Goal

Align the remaining skeleton storage path with the concrete `ModelRoot` model
type while preserving the existing storage SPI, in-memory adapter, command
executor wiring, CLI wiring, and application composition root.

The earlier `VedenemoModel` placeholder should no longer be the stored model
type after this refactor.

### Scope

- Change `ModelStorage` to save and load `ModelRoot`.
- Update `InMemoryModelStorage` to store `ModelRoot`.
- Keep `ModelStorage` and `InMemoryModelStorage` as functional concepts for
  following phases.
- Keep `CommandExecutor` constructor wiring intact unless a compile-safe minimal
  signature adjustment is required.
- Keep `VedenemoApp.createCommandExecutor()` and CLI startup behavior working.
- Remove `VedenemoModel` only if no references remain after the refactor.
- Update `docs/architecture_doc.md` after implementation so it reflects the
  current concrete implementation.

### Constraints

- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak web, JSON, Javalin, or adapter types into core or SPI.
- Keep explicit constructor wiring.
- Do not add durable persistence yet.
- Do not change the existing ModelRoot HTTP endpoints unless required by the
  refactor.

### Tests / Verification

At minimum, run:

```bash
mvn -B clean verify
```

If practical, add focused tests for `InMemoryModelStorage` storing and loading
`ModelRoot` instances.

### Planning Notes

- `ModelStorage` currently stores `VedenemoModel`.
- `InMemoryModelStorage` currently stores `VedenemoModel`.
- `CommandExecutor` currently depends on `ModelStorage`, but does not yet use it
  for real command behavior.
- The active model API flow already uses `ModelRoot` and `ModelRegistry`.

### Planning Status

Ready to move to `tasks/current-task.md` when this refactor should be executed.


## Create VEntity and VAttribute classes into vedenemo-model-api

Status: executed. Full task text retained here for history.

### Goal

Add the first model-structure classes that will later be bound under
`ModelRoot`.

This task should introduce domain model types only. Do not add REST endpoints,
UX changes, persistence, parser behavior, or model-root binding yet.

### Domain Model Changes

Add `Versionable`, an abstract base class for model elements with lifecycle
version metadata:

- `activeSince`: required `ModelVersion` since which the element is considered
  active.
- `deprecatedSince`: optional `ModelVersion` since which the element is
  considered deprecated.

When `VEntity` or `VAttribute` is created for a model, `activeSince` is expected
to come from the current `ModelVersion` of the owning `ModelRoot`. The actual
binding to `ModelRoot` is not implemented in this task; callers pass the
`ModelVersion` explicitly for now.

Add `DataType`, a Java enum:

```java
public enum DataType {
    TEXT,
    NUMERIC,
    URL,
    DATA
}
```

Add `VAttribute`, a model attribute class extending `Versionable`, with these
fields:

- `azName`: ASCII Vedenemo attribute identifier. Preserve the original case as
  entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the attribute. UTF-8 string. Must be non-blank.
  Uniqueness is not enforced (but in practise is expected to be unique inside
  a hosting entity in order to make any sense).
- `type`: `DataType`.

`VAttribute` should not know which entity hosts it and should not enforce
attribute-name uniqueness by itself. Attribute `azName` uniqueness belongs to
the hosting `VEntity`.

Add a `VEntity` class with these fields:

- `azName`: ASCII Vedenemo entity identifier. Preserve the original case as
  entered. It must start with an ASCII letter and then contain only ASCII
  letters and underscores. Numeric characters and hyphens are not allowed.
- `visName`: visual display name for the entity. UTF-8 string. Must be non-blank.
  Uniqueness is not enforced (but in practise is expected to be unique inside
  a model in order to make any sense).
- `attributes`: ordered collection of `VAttribute` instances belonging to the
  `VEntity`. It must preserve original insertion order.

`VEntity` extends `Versionable`.

`VEntity` should provide explicit operations for managing attributes:

- add a `VAttribute`
- remove a `VAttribute` by attribute `azName`
- remove a `VAttribute` by instance
- list attributes as a read-only copy in insertion order

`VEntity` must enforce attribute `azName` uniqueness case-insensitively.
`Example` and `example` conflict, but original submitted casing is preserved.

`VEntity` itself should not enforce entity-name uniqueness within a model.
Entity uniqueness will be handled later by the future container that binds
entities under `ModelRoot` or another model-level aggregate.

`VAttribute` and `VEntity` should be immutable after construction in this first
iteration. The exception is `VEntity`'s explicit attribute-management methods,
which may add and remove attributes while a model is under construction. Later
release/deprecation rules are out of scope for this task.

Implementation constraints:

- Put Vedenemo-owned model types in a pure JDK module, most likely
  `vedenemo-model-api`, unless implementation analysis finds a better existing
  boundary.
- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak Javalin, JSON-library, or HTTP types into model/core/SPI modules.
- Prefer small explicit classes.
- Reuse the existing `ModelRoot` `azName` validation rules. If practical,
  extract shared package-private validation helpers in `vedenemo-model-api` so
  `ModelRoot`, `VEntity`, and `VAttribute` do not duplicate the same low-level
  validation code.

### Tests / Verification

Add focused model API tests if practical.

Minimum test coverage:

- `VAttribute` accepts valid data.
- `VAttribute` rejects invalid `azName`.
- `VAttribute` rejects blank `visName`.
- `VAttribute` rejects missing `DataType`.
- `VEntity` accepts valid data.
- `VEntity` preserves attribute insertion order.
- `VEntity` rejects duplicate attribute `azName`.
- `VEntity` rejects case-only duplicate attribute `azName`.
- `VEntity` can remove an attribute by `azName`.
- `VEntity` can remove an attribute by instance.
- `VEntity.attributes()` returns a read-only copy.
- lifecycle versions reject invalid combinations if lifecycle ordering is
  invalid.

At minimum, run:

```bash
mvn -B clean verify
```

### Architecture Documentation

After implementation and successful verification, update
`docs/architecture_doc.md` in the same change.

The update should document only the concrete implementation that exists after
the task is complete:

- `Versionable`
- `DataType`
- `VAttribute`
- `VEntity`
- `VEntity` attribute ordering and uniqueness behavior

### Resolved Planning Decisions

- `deprecatedSince` is optional.
- `activeSince` is required.
- `activeSince` is supplied from the current `ModelVersion` of the owning
  `ModelRoot` when entities or attributes are added to a model.
- When present, `deprecatedSince` must be strictly later than `activeSince`.
  Equal versions are invalid.
- `VEntity` removal should support both `azName` and `VAttribute` instance.
- `VEntity.attributes()` should expose only a read-only `List<VAttribute>` copy.
- `VAttribute` and `VEntity` should be immutable after construction, except for
  explicit `VEntity` attribute add/remove methods during model construction.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Added `Versionable`, `DataType`, `VAttribute`, and `VEntity` to
  `vedenemo-model-api`.
- Extracted shared model text validation so `ModelRoot`, `VEntity`, and
  `VAttribute` use the same `azName` and `visName` rules.
- Made `ModelVersion` comparable so lifecycle ordering can be validated.
- Added focused model API tests for valid data, invalid names, blank display
  names, missing data type, attribute order, duplicate attributes, removal
  operations, read-only snapshots, and invalid lifecycle versions.
- Updated `docs/architecture_doc.md` to reflect the current concrete
  implementation.
- `mvn -B clean verify` passed.

## Create Session concept, bind it to CommandExecutor, and start new session via command-line interface

Status: executed. Full task text retained here for history.

### Goal

Introduce a first `Session` concept and make command execution session-aware,
then expose a minimal CLI flow that starts a session, shows the session UUID,
accepts an interactive prompt, and cleans the session up on exit.

This is a planning task. All planning questions are resolved, and the task is
ready to move to execution when selected.

### Current Implementation Context

- `vedenemo-core` currently contains `Command`, `NoOpCommand`, and
  `CommandExecutor`.
- `CommandExecutor` currently holds `ModelStorage` but does not yet perform real
  command behavior.
- `vedenemo-cli` currently starts in-process and directly constructs a
  `CommandExecutor` with `InMemoryModelStorage`.
- `vedenemo-web-api` currently exposes only model HTTP endpoints. There is no
  HTTP session API and no CLI HTTP client yet.

### Proposed Domain Model

Add a Vedenemo-owned `Session` concept in `vedenemo-core`.

Initial responsibilities:

- Each session has a unique UUID created when the session starts.
- The session tracks executed `Command` instances in execution order.
- The tracked command history must make future undo possible by walking
  executed commands backward in exact reverse order.
- The session tracks the model currently selected for modification as
  `Optional<String>` containing the selected `ModelRoot.azName`.
- Later phases may add dirty status, last save time, persisted session state, or
  richer metadata. These are out of scope for the first implementation.
- Command history should be exposed as an immutable execution-order snapshot and
  also as an immutable reverse-order snapshot prepared for future undo logic.

### Command Executor Binding

`CommandExecutor` should become session-aware.

Planning intent:

- A `CommandExecutor` instance should be bound to one active `Session`.
- The executor should execute commands in the context of its bound `Session`.
- Executed commands should be recorded into that session.
- `CommandExecutor` is primarily a backend-side concept. Commands invoked via
  HTTP API should be tracked through backend session-bound executors.
- The implementation should stay small and deterministic.
- No dependency injection framework should be introduced.
- No third-party dependencies should be added to `vedenemo-core`.

Implementation planning note:

- A backend session registry/manager should create the `Session` and the
  session-bound `CommandExecutor` together, so later HTTP command endpoints can
  resolve the executor by session UUID.

### Session Lifecycle

Add lifecycle operations for:

- create/start session
- look up an active session by UUID for backend HTTP request handling
- detach/end session
- remove session from the active session registry when ended

Planning intent:

- A process-local in-memory session registry is enough for this phase.
- Durable session persistence is out of scope.
- Distributed runtime behavior is out of scope.
- Session UUIDs should use JDK `UUID`.
- Attach-to-existing-session behavior is out of scope for the first CLI
  iteration. The CLI creates a new session on start and removes it on exit.

### CLI Behavior

`VedenemoCli` should be able to:

- Start as a standalone command-line application.
- Connect to a running Vedenemo HTTP API backend.
- Create a new backend session at startup through the HTTP API.
- Print that a session with UUID `<uuid>` was created or attached.
- Show the prompt:

```text
VedenemoCli>
```

- When the user presses Enter on an empty line, echo an empty line and show a
  new prompt.
- Support the single command `exit`.
- On `exit`, call the HTTP API to detach/end the session and remove it from the
  active backend session registry.
- Provide an implementation skeleton that can later support more CLI commands
  and multi-step interaction sequences.
- If practical, register a JVM shutdown hook so Ctrl+C attempts best-effort
  HTTP session cleanup before process exit.

### Backend Access Boundary

The CLI should connect to the backend through HTTP in this task.

- Option A: the CLI uses an in-process backend for now, through
  `vedenemo-app`/`CommandExecutor`/session registry wiring. This keeps the task
  narrow and avoids adding HTTP client behavior to the CLI.
- Option B: add HTTP session endpoints in `vedenemo-web-api`, probably through
  a new `SessionResource`, and make the CLI call a running web API process.
  This is closer to remote backend wording but broadens scope to HTTP API,
  serialization, CLI configuration, connection errors, and web tests.

Resolved decision: use Option B now. Add a `SessionResource` or equivalent HTTP
resource in `vedenemo-web-api`, and make `VedenemoCli` call a running backend
HTTP API process.

The first HTTP surface should stay minimal:

- `POST /sessions/start` creates a new backend session and returns the session
  UUID.
- `DELETE /sessions/{uuid}` ends/removes the backend session.
- No HTTP command endpoint is included in this task.
- Optional ping/lookup endpoint may be added only if needed for tests or CLI
  robustness.

### Suggested Module Placement

- Put `Session` and session lifecycle logic in `vedenemo-core`.
- Keep the process-local session registry/manager in `vedenemo-core` unless
  later persistence needs a SPI.
- Keep HTTP session endpoints, JSON mapping, and HTTP errors in
  `vedenemo-web-api`.
- Keep CLI interaction code and HTTP client calls in `vedenemo-cli`.
- The CLI should read the backend base URL from `VEDENEMO_API_BASE_URL`, with
  default `http://127.0.0.1:8080`.
- Keep application wiring in `vedenemo-app`, `vedenemo-web-api`, or
  `vedenemo-cli`.
- Do not put CLI or HTTP framework types into `vedenemo-core`.

### Tests / Verification

Add focused backend tests where practical.

Minimum intended coverage:

- creating a session assigns a UUID
- ending/removing a session removes it from the active session registry
- command execution records commands in the active session
- command history is exposed in deterministic execution order or reverse order
  as needed for future undo
- selected model starts empty if no model is selected
- selected model can be changed if this is implemented in the first pass
- HTTP session creation returns a UUID
- HTTP session end removes the backend session
- CLI command loop can be smoke-tested without hanging, if practical

At minimum, run:

```bash
mvn -B clean verify
```

CLI smoke verification should be added if practical, but must not make the test
suite hang on interactive input.

### Architecture Documentation

After implementation and successful verification, update
`docs/architecture_doc.md` in the same change.

The update should document only concrete implementation that exists after the
task is complete:

- `Session`
- session registry or manager
- how `CommandExecutor` relates to `Session`
- HTTP session endpoints
- CLI startup/session lifecycle flow through HTTP

### Resolved Planning Decisions

- The CLI should use HTTP API access in this task.
- Add backend HTTP session endpoints now, using `SessionResource` or an
  equivalent resource in `vedenemo-web-api`.
- Use `POST /sessions/start` for session creation.
- Use `DELETE /sessions/{uuid}` for session cleanup.
- The CLI reads the backend base URL from `VEDENEMO_API_BASE_URL`, defaulting to
  `http://127.0.0.1:8080`.
- `CommandExecutor` remains primarily backend-side and tracks commands invoked
  through HTTP API.
- Store selected model as `Optional<String>` containing model `azName`.
- Bind each `CommandExecutor` instance to one active `Session`.
- First CLI iteration only needs create-on-start and remove-on-exit.
- Attach-to-existing-session behavior is out of scope.
- Command history should expose immutable execution-order and reverse-order
  snapshots.
- Do not add an HTTP command endpoint for `NoOpCommand` in this task. Implement
  only session lifecycle endpoints plus backend-side executor binding.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Added `Session` and `SessionManager` to `vedenemo-core`.
- Bound `CommandExecutor` to one active `Session` and made `execute` record
  commands in that session.
- Added immutable execution-order and reverse-order command history snapshots.
- Added selected model tracking as optional model `azName`.
- Added `POST /sessions/start` and `DELETE /sessions/{uuid}` in
  `vedenemo-web-api`.
- Updated `vedenemo-cli` to create and clean up backend sessions through the
  HTTP API using `VEDENEMO_API_BASE_URL`, defaulting to
  `http://127.0.0.1:8080`.
- Added focused core, CLI, and web API tests.
- Updated `docs/architecture_doc.md` to reflect the current concrete
  implementation.
- `mvn -B clean verify` passed.
- Live local smoke test for session start/delete passed after running outside
  the sandbox because sandbox socket binding was blocked.

## Adding support for adding new models and listing existing models to VedenemoCli

Status: executed. Full task text retained here for history.

### Goal

Add first useful model-management commands to `VedenemoCli` while preserving the
existing HTTP-backed session startup and cleanup behavior.

The CLI should be able to:

- list existing models from the backend
- add a new model through the backend HTTP API
- attach the current CLI session to one listed model
- detach the current CLI session from the attached model
- show help for available commands

This is a planning task. All planning questions are resolved, and the task is
ready to move to execution when selected.

### Current Implementation Context

- `VedenemoCli` currently starts an HTTP-backed backend session using
  `POST /sessions/start`.
- The CLI currently supports only empty lines and `exit`.
- The CLI already reads `VEDENEMO_API_BASE_URL`, defaulting to
  `http://127.0.0.1:8080`.
- The web API already exposes:
  - `POST /models/add`
  - `GET /models/list`
  - `POST /sessions/start`
  - `DELETE /sessions/{uuid}`
- `Session` already stores selected model as `Optional<String>` containing
  model `azName`, but no HTTP endpoint currently updates that selected model.

### CLI Commands

All previously available CLI behavior must keep working:

- startup creates a backend session
- empty line returns a new prompt
- `exit` cleans up the backend session and exits

Add these new commands.

#### `list`

Lists all currently added / loaded models from the backend as a numbered list.

Each row should show:

- running number `N`
- visible name / `visName`
- ASCII name / `azName`
- version, if useful and already returned by the backend

Example output shape:

```text
1. Example Model (Example_Model) version 1.0.0
2. Sales Model (Sales_Model) version 1.0.0
```

If there are no models, print a clear message such as:

```text
No models available.
```

The numbering should be deterministic and based on the backend list order.

#### `attach [N | azName]`

Associates the current CLI session with an existing model.

Supported forms:

- `attach N` attaches by the running number from the latest model list.
- `attach azName` attaches by model `azName`.
- `attach` with no argument asks the user for a number or `azName`.

Expected behavior:

- If there are no models, report that there are no models to attach.
- If a numeric argument does not match any listed model number, report a clear
  error.
- If an `azName` argument does not match any existing model, report a clear
  error.
- After successful attach, the prompt changes to:

```text
VedenemoCli[azName]>
```

- Successful attach must update backend `Session.selectedModelAzName` through
  HTTP.
- `attach N` always refers to the most recent `list` output. If no list has been
  loaded yet, report that the user must run `list` first or attach by `azName`.

#### `detach`

Detaches the current CLI session from the previously attached model.

Expected behavior:

- If a model is attached, clear the attached model and return the prompt to:

```text
VedenemoCli>
```

- If no model is attached, print a clear message that there is no attached
  model.
- Successful detach must update backend `Session.selectedModelAzName` through
  HTTP.

Only the correctly spelled `detach` command is supported. Do not add `detatch`
as an alias.

#### `add`

Adds a new model through the backend HTTP API.

Interactive flow:

1. Ask for `visName`.
2. Generate a valid ASCII `azName` suggestion from the entered `visName`.
3. Ask for `azName`, showing the suggestion.
4. If the user presses Enter without typing a replacement, use the suggestion.
5. If the user types a replacement, use the typed value.
6. Create the model with version `1.0.0`.
7. After successful creation, automatically attach the CLI session to the new
   model, update backend `Session.selectedModelAzName`, and update the prompt
   to `VedenemoCli[azName]>`.

The `add` command should handle backend validation errors, including duplicate
`azName`, and show a readable message without exiting the CLI.

Suggested `azName` generation rule for planning:

- transliterate only by simple ASCII filtering for now; no third-party
  dependency
- split the visual name into ASCII letter runs
- join runs with underscores
- ensure the result starts with an ASCII letter
- if no valid ASCII letter exists, fall back to a prompt asking the user to
  enter `azName` manually
- preserve the user's final typed casing

This should align with the existing `ModelRoot` rule: starts with an ASCII
letter and then contains only ASCII letters and underscores. Digits and hyphens
are not allowed.

#### `help`

Lists all available commands with short explanations.

Minimum commands to show:

- `list`
- `add`
- `attach [N | azName]`
- `detach`
- `help`
- `exit`

### Backend / HTTP Scope

The existing model endpoints are enough for listing and adding:

- `GET /models/list`
- `POST /models/add`

Add backend session-selection endpoints in this task so attach/detach are
reflected in backend `Session.selectedModelAzName`.

Suggested endpoint shape:

- `PUT /sessions/{uuid}/selected-model`
  - request body contains `azName`
  - validates that the session exists
  - validates that the model exists in the process-local `ModelRegistry`
  - updates `Session.selectedModelAzName`
- `DELETE /sessions/{uuid}/selected-model`
  - validates that the session exists
  - clears `Session.selectedModelAzName`

The exact request/response DTO shape can be chosen during implementation, but
HTTP and JSON details must stay in `vedenemo-web-api`.

### Suggested CLI Structure

The current `VedenemoCliApp` has a simple prompt loop. This task can extend that
structure, but should keep the code testable without hanging on stdin.

Suggested implementation direction:

- Add a model HTTP client abstraction in `vedenemo-cli`, similar to
  `SessionClient`.
- Extend the session HTTP client abstraction to support selecting and clearing
  the selected model.
- Add small CLI command handling methods/classes for `list`, `add`, `attach`,
  `detach`, `help`, and `exit`.
- Keep JSON parsing/writing in the CLI small and explicit. If possible, use JDK
  APIs and simple structured parsing compatible with the known backend response
  shape rather than adding a new CLI JSON dependency.
- Keep backend HTTP framework and Jackson types out of the CLI.

### Tests / Verification

Add focused CLI tests where practical.

Minimum intended coverage:

- `help` prints all commands.
- `list` prints an empty-list message when there are no models.
- `list` prints numbered models when models exist.
- `attach N` attaches to the numbered model and updates the prompt.
- `attach azName` attaches to the named model and updates the prompt.
- `attach` with no argument asks for a model identifier.
- `attach N` without a previous `list` prints a clear message and does not
  fetch automatically.
- successful attach updates backend selected model state.
- invalid `attach` input prints a clear message and keeps the previous prompt.
- `detach` clears the prompt when a model is attached.
- `detach` with no attached model prints a clear message.
- successful detach clears backend selected model state.
- `add` prompts for `visName`, suggests `azName`, creates model version
  `1.0.0`, attaches to the created model, and updates backend selected model
  state.
- backend validation failure during `add` is reported without exiting the CLI.
- backend tests verify session selected-model set/clear endpoints.
- `exit` still cleans up the backend session.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a local smoke test with the backend JAR and the CLI command
flow. Any smoke test must not require manual input in CI.

### Documentation

After implementation:

- Create a separate CLI reference document, preferably `docs/cli-reference.md`.
- Document `list`, `add`, `attach`, `detach`, `help`, and `exit`.
- Include examples of adding a model, listing models, attaching by number,
  attaching by `azName`, detaching, and exiting.
- Update `README.md` with a short link to the CLI reference instead of
  duplicating all command details there.

### Architecture Documentation

If implementation adds new HTTP endpoints or changes current component
responsibilities, update `docs/architecture_doc.md` in the same change.

At minimum, update it if:

- new session model-selection endpoints are added
- CLI command handling becomes a distinct component worth documenting
- CLI/backend runtime flow materially changes

### Resolved Planning Decisions

- `attach` and `detach` must update backend `Session.selectedModelAzName`
  through HTTP endpoints.
- Only `detach` is supported. Do not add typo alias `detatch`.
- For `attach N`, `N` always refers to the most recent `list` output.
- `attach N` must not fetch the model list automatically if no list exists.
- After `add` creates a model, auto-attach must update backend selected model
  state as well as local CLI prompt state.
- Create a separate CLI reference document now and link to it from `README.md`.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Added CLI model-management commands: `list`, `add`, `attach`, `detach`, and
  `help`.
- Added CLI HTTP model client support for `GET /models/list` and
  `POST /models/add`.
- Extended CLI session HTTP support for selecting and clearing the backend
  session selected model.
- Added backend selected-model endpoints:
  - `PUT /sessions/{uuid}/selected-model`
  - `DELETE /sessions/{uuid}/selected-model`
- Added focused CLI and web API tests for the new command and endpoint
  behavior.
- Added `docs/cli-reference.md`, linked it from `README.md`, and updated the
  current implementation architecture document.
- `mvn -B clean verify` passed.

## Taking Command concept into use and implementing the first command

Status: executed. Full task text retained here for history.

### Goal

`vedenemo-core` already has placeholder command concepts:

- `Command`
- `NoOpCommand`
- `CommandExecutor`
- session-bound command history in `Session`

This task should take the command concept into real use by adding the first
model-changing command: create a new `VEntity` in the currently selected model.

This is still a planning task. The current implementation has `ModelRoot`
metadata and standalone `VEntity` / `VAttribute` classes, but `ModelRoot` does
not yet contain entities. This task will extend `ModelRoot` so it directly owns
entities in this first iteration.

### Current Implementation Context

- `ModelRoot` currently contains only `azName`, `visName`, and `ModelVersion`.
- `VEntity` exists and can hold ordered attributes, but no current model object
  owns `VEntity` instances.
- `ModelRegistry` stores process-local `ModelRoot` instances in insertion order.
- `ModelStorage` and `InMemoryModelStorage` store and load `ModelRoot`.
- `Session` stores the currently selected model as `Optional<String>` containing
  `ModelRoot.azName`.
- `Session` records executed `Command` instances and can expose command history
  in reverse order for future undo behavior.
- `CommandExecutor` is bound to one `Session`, but currently only records a
  command and does not mutate a model.
- `vedenemo-web-api` already has model add/list endpoints and session lifecycle
  / selected-model endpoints.
- `VedenemoCli` already has context-dependent `add`: without a selected model it
  adds a model and attaches to it.

### Proposed Domain / Model Scope

Add the smallest concrete structure needed for a model to own entities.

- Extend `ModelRoot` so it directly contains and manages ordered `VEntity`
  instances.
- Add explicit operations such as `addEntity(VEntity)`,
  `removeEntity(String azName)`, `removeEntity(VEntity)`, and `entities()`.
- Preserve insertion order and enforce entity `azName` uniqueness
  case-insensitively inside one model.
- `entities()` should expose a read-only `List<VEntity>` copy in insertion
  order.
- Entity `azName` uniqueness is enforced case-insensitively, while original
  casing is preserved.

The separate model aggregate/document option is intentionally not introduced in
this task. It may be revisited later if persistence or save/load boundaries need
a wrapper around `ModelRoot`.

### Command Architecture Goals

Add a `CreateEntityCommand`.

Initial command data:

- target model `azName`
- new entity `azName`
- new entity `visName`

The command should not carry `activeSince`; command execution derives
`activeSince` from the current version of the target model.

The created entity starts with no attributes.

Add a `DeleteEntityCommand` as the undo counterpart for `CreateEntityCommand`.

Expected command execution behavior:

- `CommandExecutor.execute(command)` validates and applies the command to the
  selected model.
- A successfully applied command is recorded into the session command history.
- A failed command is not recorded.
- `CommandExecutor` can undo the latest undoable command by applying the correct
  inverse command.
- Undo removes the latest created entity for this first command iteration.
- Undo removes the original command from active command history, so session
  command history represents the currently applied command state.
- If there is no command to undo, the executor reports that no undo operation
  was available.

`Session` currently exposes command history snapshots but does not support
popping the latest command. This task should add the minimal history operation
needed by undo while preserving read-only snapshot access for callers.

`DeleteEntityCommand` is only an internal inverse command for undo in this task.
It is not exposed as a user-executable CLI command yet.

### Backend / HTTP API Scope

Command execution is initiated through `vedenemo-web-api`.

Use command-specific endpoints for this phase.

Example shape:

```text
POST /sessions/{uuid}/commands/create-entity
```

This keeps request validation and JSON handling explicit, avoids reflection or
framework-specific polymorphic JSON behavior, and keeps the first implementation
small. A generic command envelope may be introduced later when there are enough
commands to justify it.

Undo endpoint:

```text
POST /sessions/{uuid}/commands/undo
```

Expected HTTP results:

- `200` when undo succeeds
- `304` when there is no command available to undo
- clear client error when session/model/command input is invalid
- unexpected status codes should remain visible to CLI users

### CLI Scope

Extend `VedenemoCli` command behavior:

- when no model is attached, `add` keeps its current behavior and creates a new
  model
- when a model is attached, `add` should add a new entity to the selected model
  by sending a backend command request
- add a new `undo` CLI command that asks the backend to undo the latest executed
  command for the active session

Proposed interactive `add` flow when a model is attached:

1. Ask for entity visible name.
2. Generate a valid ASCII `azName` suggestion using the same style as model add.
3. Ask for entity `azName`, showing the suggestion.
4. If the user presses Enter, use the suggestion.
5. Send the create-entity command to the backend.
6. On success, print:

```text
Entity <azName> added.
```

CLI-side HTTP request construction should stay separate from backend/core
command records for now. Executable behavior belongs on the backend. Keep the
HTTP field names stable and aligned with command record fields so future command
save/load support has a clear path.

### Serialization / Future Persistence Consideration

Commands are initially serialized only for HTTP traffic, but future CLI features
should be able to save and load both model data and executed command history to
external files.

Planning implications:

- command payloads should use stable Vedenemo-owned field names
- command types should have explicit stable type names if a generic command
  envelope is introduced
- avoid reflection-heavy JSON polymorphism
- keep command data separate from execution-only runtime dependencies
- command records should be pure JDK / Vedenemo-owned types

### Tests / Verification

Add focused core tests where practical:

- `CreateEntityCommand` adds an entity to the selected model.
- created entity uses the current model version as `activeSince`.
- created entity starts with no attributes.
- duplicate entity `azName` is rejected.
- failed command is not recorded in session history.
- successful command is recorded in session history.
- undo after create removes the created entity.
- undo with no undoable command reports no-op / unavailable.

Add focused web API tests:

- create-entity command endpoint succeeds for an active session with selected
  model.
- create-entity command endpoint rejects missing session.
- create-entity command endpoint rejects no selected model.
- create-entity command endpoint rejects invalid entity input.
- undo endpoint succeeds after create.
- undo endpoint returns `304` when nothing can be undone.

Add focused CLI tests:

- `add` without an attached model keeps current model-add behavior.
- `add` with an attached model prompts for entity data and sends create-entity.
- duplicate or invalid entity errors are printed without exiting the CLI.
- `undo` prints success when backend undo succeeds.
- `undo` prints a clear message when backend returns `304`.
- unexpected undo status is shown to the user.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model
- add entity while attached
- undo
- exit

### Architecture Documentation

After implementation, update `docs/architecture_doc.md` in the same change if
this task changes current concrete architecture. It likely will, because it
introduces real command execution, model entity ownership, backend command
endpoints, undo behavior, and CLI command execution flow.

### Resolved Planning Decisions

- `ModelRoot` should directly own `VEntity` instances in this iteration.
- Do not introduce a separate model aggregate/document type yet.
- Use command-specific HTTP endpoints for this phase.
- Undo removes the original command from session command history, so active
  command history represents the currently applied state.
- `DeleteEntityCommand` is only an internal inverse command for undo in this
  task.
- CLI should keep HTTP request DTO construction separate from backend/core
  command records for now.
- The CLI success message after adding an entity is:

```text
Entity <azName> added.
```

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Extended `ModelRoot` to directly own ordered `VEntity` instances with
  case-insensitive entity `azName` uniqueness, remove operations, and read-only
  snapshot listing.
- Added `CreateEntityCommand`, internal `DeleteEntityCommand`, and `UndoResult`
  in `vedenemo-core`.
- Updated `CommandExecutor` to apply create-entity commands to the selected
  model, record only successful commands, and undo the latest create-entity
  command by removing the created entity and removing the original command from
  active history.
- Added session history support for peeking/removing the latest command.
- Wired `SessionManager` and `CommandExecutor` to the process-local
  `ModelRegistry` used by the web API.
- Added `POST /sessions/{uuid}/commands/create-entity` and
  `POST /sessions/{uuid}/commands/undo`.
- Added CLI command transport, context-dependent `add` for attached-model entity
  creation, and `undo`.
- Added focused model, core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed.

## Add VAttribute Commands And CLI Entity Context

Status: executed. Full task text retained here for history.

### Goal

Extend the existing command execution path so users can add `VAttribute` items
to an existing `VEntity` through `VedenemoCli`, using the backend HTTP API in
the same command-oriented style already used for adding entities to a model.

This task should also add the internal attribute removal operation needed to
undo attribute creation. User-visible attribute removal as a separate edit
operation is deferred until it can be recorded and undone as its own command.

### Current Implementation Context

- `ModelRoot` directly owns ordered `VEntity` instances.
- `VEntity` already owns ordered `VAttribute` instances and enforces attribute
  `azName` uniqueness case-insensitively.
- `VAttribute` already contains `azName`, `visName`, `DataType`, and lifecycle
  metadata from `Versionable`.
- `Session` stores the currently selected model as `Optional<String>`.
- `CommandExecutor` can execute `CreateEntityCommand` against the selected
  model and can undo it through internal `DeleteEntityCommand`.
- `vedenemo-web-api` exposes command-specific endpoints:
  - `POST /sessions/{uuid}/commands/create-entity`
  - `POST /sessions/{uuid}/commands/undo`
- `VedenemoCli` uses HTTP-backed sessions. `add` creates a model when detached
  and creates an entity when attached to a model.

### Domain / Model Scope

No new model types should be required for this task.

Use the existing `VEntity` attribute-management operations:

- add a `VAttribute`
- remove a `VAttribute` by attribute `azName`
- remove a `VAttribute` by instance
- list attributes as a read-only copy in insertion order

Expected behavior:

- Adding an attribute preserves insertion order inside the owning entity.
- Attribute `azName` uniqueness remains enforced case-insensitively inside one
  entity.
- Created attributes derive `activeSince` from the current version of the
  owning `ModelRoot`.
- Created attributes start with no additional metadata beyond current
  `VAttribute` fields.
- Removing an attribute removes the current attribute from the owning entity.
- Durable persistence, save/load formats, parser syntax, and UI visualization
  remain out of scope.

### Command Architecture Goals

Add `CreateAttributeCommand`.

Initial command data:

- target model `azName`
- target entity `azName`
- new attribute `azName`
- new attribute `visName`
- new attribute `DataType`

The command should not carry `activeSince`; command execution derives it from
the current version of the target model.

Add `DeleteAttributeCommand`.

Initial command data:

- target model `azName`
- target entity `azName`
- target attribute `azName`

Expected command execution behavior:

- `CommandExecutor.execute(command)` validates and applies
  `CreateAttributeCommand` to an entity in the selected model.
- `DeleteAttributeCommand` is the internal undo counterpart for
  `CreateAttributeCommand` in this task.
- `DeleteAttributeCommand` does not need to be user-executable or recorded as a
  separate command in this task.
- Successful commands are recorded in session command history.
- Failed commands are not recorded.
- Undo of `CreateAttributeCommand` removes the created attribute by deriving and
  applying the delete counterpart at undo time, then removes the original create
  command from active history.
- Undo always operates on the latest successfully executed command only. Treat
  session command history as a stack: undo pops the topmost command and cannot
  undo a command from the middle of the history.
- `CreateAttributeCommand` must retain enough target identity to derive its undo
  counterpart later:
  - target model `azName`
  - target entity `azName`
  - target attribute `azName`
- `DeleteAttributeCommand` does not need to be created or stored when the
  attribute is originally added. It can be constructed only when undo is
  actually requested.
- User-visible attribute deletion as a later edit operation is a separate
  concern. When introduced, it must be recorded as the latest executed command
  and must have enough undo data to restore the deleted attribute.

Resolved decision for this task:

- Keep `DeleteAttributeCommand` as the internal counterpart operation used for
  undoing `CreateAttributeCommand`.
- Keep undo support in this task focused on undoing newly created attributes.
- Defer user-visible attribute removal to a later task. That later task should
  introduce whatever command-result or undo-record structure is needed to retain
  the removed `VAttribute` and original insertion position.

### Backend / HTTP API Scope

Keep command execution in `vedenemo-web-api` behind command-specific endpoints.

Add:

```text
POST /sessions/{uuid}/commands/create-attribute
```

Suggested create-attribute request body:

```json
{
  "entityAzName": "Customer",
  "attributeAzName": "Email",
  "attributeVisName": "Email",
  "dataType": "TEXT"
}
```

If `dataType` is missing or blank, the backend should default it to `TEXT`.
If present, the backend should accept case-insensitive enum names and aliases
such as `text`, `number`, `url`, and `data`.

Expected HTTP behavior:

- `200` when command execution succeeds.
- `400` for invalid command input, no selected model, missing target entity,
  duplicate attribute `azName`, or unsupported data type.
- `404` for missing session.
- Existing `POST /sessions/{uuid}/commands/undo` should undo attribute creation
  in addition to entity creation.
- Do not add a user-facing delete-attribute command endpoint in this task.
- JSON parsing/serialization stays in `vedenemo-web-api`.
- Core command records remain pure JDK / Vedenemo-owned types.

### CLI Scope

Add a clear way to select an entity as the target for attribute operations.

Recommended CLI model:

- Keep the existing attached model prompt:

```text
VedenemoCli[Model_AzName]>
```

- Add an optional attached entity context and show it in the prompt:

```text
VedenemoCli[Model_AzName/Entity_AzName]>
```

Recommended commands:

- `entities`
  - Lists entities in the currently attached model as a numbered list.
  - Requires an attached model.
- `entity [N | azName]`
  - Selects the target entity for attribute operations.
  - `entity N` uses the most recent `entities` output.
  - `entity azName` resolves by entity `azName`.
  - `entity` with no argument asks for a number or `azName`.
- `entity detach`
  - Clears the selected entity while keeping the model attached.
- `attributes`
  - Lists attributes in the selected entity.
  - Requires both an attached model and selected entity.
- `attr add`
  - Adds a new attribute to the selected entity.
  - Prompts for visible name, suggests `azName`, asks for final `azName`, then
    asks for `DataType`.
  - If `DataType` is left blank, default to `TEXT`.
  - Accept case-insensitive data type aliases such as `text`, `number`, `url`,
    and `data`; normalize them to the existing `DataType` enum values.

Expected CLI behavior:

- `add` should keep its current meanings:
  - detached from a model: add a model
  - attached to a model but no entity selected: add an entity
- Attribute creation should use `attr add` instead of adding a third contextual
  meaning to `add`. This keeps the prompt behavior predictable and avoids
  surprising users when an entity is selected.
- If `attr add` submits an attribute `azName` that overlaps an existing
  attribute name case-insensitively, the backend should reject the command with
  a clear `400` validation response and the CLI should print a clear failure
  message without exiting.
- Recommended duplicate-name CLI flow:
  1. User runs `attr add`.
  2. CLI asks for attribute visible name, suggests an `attributeAzName`, asks
     for the final `attributeAzName`, then asks for `DataType`.
  3. CLI sends the create-attribute command to the backend.
  4. Backend detects the duplicate inside the target `VEntity` and returns a
     clear validation error such as `attribute azName must be unique within
     VEntity`.
  5. CLI prints `Attribute was not added: <backend error>.`
  6. CLI keeps the current model/entity context and returns to the prompt.
  7. The failed command is not recorded in backend session history, so `undo`
     is unaffected by the failed add.
- Do not automatically re-prompt inside the same `attr add` interaction in this
  task. The user can run `attributes` to inspect the current state or run
  `attr add` again with another name.
- `detach` should continue clearing the selected model. It should also clear
  any selected entity.
- `undo` should keep calling the backend undo endpoint.
- Undo should always target only the latest successfully executed command in the
  backend session history.
- Successful attribute creation prints:

```text
Attribute <azName> added.
```

- Successful entity detach prints:

```text
Entity detached.
```

- `help` should include the new entity and attribute commands.

### Read / Listing API Scope

The CLI needs a way to list entities and attributes before selecting targets by
number.

Preferred narrow endpoints:

```text
GET /models/{modelAzName}/entities
GET /models/{modelAzName}/entities/{entityAzName}/attributes
```

Expected behavior:

- Entity listing returns deterministic insertion order from `ModelRoot`.
- Attribute listing returns deterministic insertion order from `VEntity`.
- Attribute listing should include `DataType` and lifecycle version fields so
  CLI output can show the current attribute state.
- Entity and attribute response DTOs should include visible names and ASCII
  names.
- These listing endpoints are read-only model API endpoints, not command
  endpoints.

Alternative for review:

- Return entity and attribute summaries from existing model list responses.
  This is likely less focused and may make `/models/list` too heavy for the
  current phase.

### Serialization / Future Persistence Consideration

Command payloads should continue to use stable Vedenemo-owned field names:

- `modelAzName`
- `entityAzName`
- `attributeAzName`
- `attributeVisName`
- `dataType`

Avoid framework-specific polymorphic command serialization. If a generic command
envelope is introduced later, give command types stable explicit names.

### Tests / Verification

Add focused model/core tests where practical:

- `CreateAttributeCommand` adds an attribute to an entity in the selected model.
- Created attribute uses the target model version as `activeSince`.
- Created attribute has the requested `DataType`.
- Duplicate attribute `azName` is rejected.
- Missing target entity is rejected.
- Failed create-attribute commands are not recorded in session history.
- Successful create-attribute commands are recorded in session history.
- Undo after create-attribute removes the created attribute.
- Undo operates only on the latest command in the session history stack.
- `DeleteAttributeCommand` removes an existing attribute when used internally as
  the undo counterpart for `CreateAttributeCommand`.

Add focused web API tests:

- create-attribute command endpoint succeeds for an active session with selected
  model and existing entity.
- create-attribute rejects missing session.
- create-attribute rejects no selected model.
- create-attribute rejects missing entity.
- create-attribute rejects invalid attribute input.
- create-attribute rejects unsupported data type.
- create-attribute defaults missing or blank data type to `TEXT`.
- create-attribute accepts case-insensitive data type aliases.
- undo endpoint succeeds after create-attribute.
- entity listing endpoint returns created entities in order.
- attribute listing endpoint returns created attributes in order, including
  `DataType` and lifecycle version fields.

Add focused CLI tests:

- `entities` requires an attached model.
- `entities` prints numbered entity rows.
- `entity N` selects from the latest entity list.
- `entity azName` selects by name.
- `entity detach` clears entity context but keeps model context.
- prompt includes selected model and selected entity when both are selected.
- `attributes` requires selected entity.
- `attributes` prints numbered attribute rows with `DataType` and lifecycle
  version fields.
- `attr add` prompts for attribute data and sends create-attribute.
- `attr add` defaults blank data type input to `TEXT`.
- `attr add` accepts case-insensitive data type aliases.
- `attr add` prints `Attribute was not added: <backend error>.` when the
  backend rejects a duplicate attribute `azName`.
- failed `attr add` keeps the current selected model/entity context.
- failed `attr add` is not undoable because the backend must not record failed
  commands.
- backend validation failures are printed without exiting the CLI.
- `detach` clears both selected model and selected entity.
- `undo` reports successful undo of attribute creation.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model
- add entity
- select entity
- add attribute
- list attributes
- undo an attribute creation
- exit

### Documentation

After implementation:

- Update `docs/cli-reference.md` with entity selection and attribute commands.
- Update `docs/architecture_doc.md` because this task adds attribute command
  execution, attribute command endpoints, read/list endpoints for model
  internals, and a CLI entity context.

Before updating `docs/architecture_doc.md`, read and follow
`docs/architecture_doc_instructions.md`.

### Resolved Planning Decisions

- `DeleteAttributeCommand` is only the internal counterpart operation for
  undoing `CreateAttributeCommand` in this task.
- User-visible attribute deletion is deferred to a later edit-operation task.
- Undo is stack-based and always applies only to the latest successfully
  executed command.
- Clearing entity context uses `entity detach`.
- Attribute listing should show `DataType` and lifecycle version fields.
- Attribute data type input accepts case-insensitive aliases.
- Blank attribute data type input defaults to `TEXT`.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Promoted the task to `tasks/current-task.md` and executed it.
- Added `CreateAttributeCommand` and internal `DeleteAttributeCommand` to
  `vedenemo-core`.
- Updated `CommandExecutor` so create-attribute commands add `VAttribute`
  instances to an existing entity in the selected model.
- Kept undo stack-based; undo of create-attribute derives and applies the
  internal delete counterpart at undo time.
- Added `POST /sessions/{uuid}/commands/create-attribute`.
- Added read-only listing endpoints:
  - `GET /models/{modelAzName}/entities`
  - `GET /models/{modelAzName}/entities/{entityAzName}/attributes`
- Added CLI entity context with `entities`, `entity [N | azName]`,
  `entity detach`, `attributes`, and `attr add`.
- Added attribute data type normalization with blank/missing default `TEXT` and
  case-insensitive aliases.
- Added focused core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed during implementation.

## Improve CLI azName Suggestions And Undo Feedback

Status: executed. Full task text retained here for history.

### Goal

Improve two CLI usability details:

- `azName` suggestions should preserve useful numeric suffixes from visible
  names, so `Attribute 2` suggests `Attribute_2` instead of `Attribute`.
- `undo` output should describe what kind of operation was undone instead of
  only printing `Undo completed.`

### Current Implementation Context

- `VedenemoCliApp.suggestAzName(String)` currently keeps only ASCII letters and
  uses non-letter runs as separators.
- Current model validation rules for `azName` do not allow digits, but the CLI
  now suggests names for models, entities, and attributes.
- The user-visible desired suggestion `Attribute_2` implies that the model
  `azName` validation rules should allow digits after the first ASCII letter,
  or at minimum that the CLI suggestion behavior and validation rules must be
  reconciled before implementation.
- `CommandExecutor.undoLatest()` currently returns only `UndoResult.UNDONE` or
  `UndoResult.NOTHING_TO_UNDO`.
- `SessionResource` maps successful undo to a generic JSON response:

```json
{"status":"undone"}
```

- `HttpCommandClient.undo()` maps the response to `UndoCommandResult.UNDONE`.
- `VedenemoCliApp.undo()` prints only:

```text
Undo completed.
```

### azName Suggestion Scope

Update CLI `azName` suggestion behavior so numeric runs are preserved when they
occur after the suggested name already starts with an ASCII letter.

Recommended behavior:

- `Attribute 2` suggests `Attribute_2`.
- `Address Line 1` suggests `Address_Line_1`.
- `2 Attribute` does not start with `2`; it should suggest `Attribute` if there
  is a later ASCII-letter run.
- `Model 2026 Draft` suggests `Model_2026_Draft`.
- non-ASCII characters remain ignored for now, preserving the current
  no-transliteration behavior.
- repeated separators collapse to one underscore.
- trailing underscores are removed.

Implementation note:

- If digits are allowed in CLI suggestions, update the shared model `azName`
  validation rules so `ModelRoot`, `VEntity`, and `VAttribute` all allow ASCII
  digits after the first ASCII letter.
- Preserve the rule that `azName` must start with an ASCII letter.
- Keep hyphens invalid.

### Undo Feedback Scope

Make undo return enough Vedenemo-owned information for the CLI to print a
specific message.

Recommended core behavior:

- Replace or extend `UndoResult` so successful undo can describe the undone
  command kind and target.
- Keep `NOTHING_TO_UNDO` behavior for empty command history.
- Preserve stack-based undo: only the latest successfully executed command can
  be undone.
- Failed undo operations should still be reported as client-visible errors and
  should not silently remove command history.

Recommended backend response shape:

```json
{
  "status": "undone",
  "undoneCommand": "create-attribute",
  "modelAzName": "Example_Model",
  "entityAzName": "Customer",
  "attributeAzName": "Email"
}
```

For entity creation undo:

```json
{
  "status": "undone",
  "undoneCommand": "create-entity",
  "modelAzName": "Example_Model",
  "entityAzName": "Customer"
}
```

Recommended CLI messages:

```text
Undo completed: removed entity Customer from model Example_Model.
```

```text
Undo completed: removed attribute Email from entity Customer in model Example_Model.
```

`Nothing to undo.` remains unchanged for HTTP `304`.

### Backend / HTTP Scope

- Update `POST /sessions/{uuid}/commands/undo` to return the richer undo
  response when undo succeeds.
- Keep `304` when no command is available to undo.
- Keep clear client error responses when undo fails due invalid current model
  state.
- HTTP DTOs stay in `vedenemo-web-api`.
- Core undo result types stay pure JDK / Vedenemo-owned types.

### CLI Scope

- Update `suggestAzName` and its tests so digits after the first ASCII letter
  are preserved.
- Update `HttpCommandClient.undo()` and `UndoCommandResult` or equivalent CLI
  result type so the CLI can read the richer undo response.
- Update `VedenemoCliApp.undo()` to print operation-specific undo messages.
- Keep unexpected undo response statuses visible to CLI users.

### Tests / Verification

Add focused model/core tests where practical:

- `azName` validation accepts digits after the first ASCII letter.
- `azName` validation still rejects names that start with a digit.
- `azName` validation still rejects hyphens.
- undo after create-entity reports the undone operation kind and target.
- undo after create-attribute reports the undone operation kind and target.
- undo with no command still reports nothing to undo.

Add focused web API tests:

- undo after create-entity returns `undoneCommand` and entity target fields.
- undo after create-attribute returns `undoneCommand` and attribute target
  fields.
- undo with no command still returns `304`.

Add focused CLI tests:

- `Attribute 2` suggests `Attribute_2`.
- `Address Line 1` suggests `Address_Line_1`.
- leading digits are not used before the first ASCII letter.
- undo after entity creation prints an entity-specific message.
- undo after attribute creation prints an attribute-specific message that
  includes the entity and model names.
- `Nothing to undo.` remains unchanged.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a non-interactive local backend plus CLI smoke test for:

- add model with numeric suffix
- add entity with numeric suffix
- select entity
- add attribute with numeric suffix
- undo attribute creation and verify specific undo output
- undo entity creation and verify specific undo output
- exit

### Documentation

After implementation:

- Update `docs/cli-reference.md` to document numeric `azName` suggestions and
  operation-specific undo output.
- Update `docs/architecture_doc.md` if undo result shape or model naming rules
  change in concrete architecture.

Before updating `docs/architecture_doc.md`, read and follow
`docs/architecture_doc_instructions.md`.

### Resolved Planning Decisions

- Digits after the first ASCII letter are valid for all `azName` values in
  `ModelRoot`, `VEntity`, and `VAttribute`.
- Use the recommended CLI undo wording.
- Include the model name in attribute undo output so non-interactive logs remain
  self-contained.
- Use HTTP/API slug names, such as `create-entity` and `create-attribute`, as
  stable command identifiers in backend undo responses.

### Command Naming Options Considered

The stable command naming scheme for undo responses was resolved after
considering these options:

Options:

- HTTP/API slug names, such as `create-entity` and `create-attribute`.
  - Pros: stable, language-neutral, already matches current command endpoint
    names, good for logs and future serialized command history.
  - Cons: not identical to Java type names, so core needs an explicit mapping.
- Java-like command type names, such as `CreateEntityCommand` and
  `CreateAttributeCommand`.
  - Pros: maps directly to current Java records and is easy to produce from
    core code.
  - Cons: leaks implementation naming into HTTP/log output and is less stable
    if Java classes are renamed.
- Enum-style stable constants, such as `CREATE_ENTITY` and `CREATE_ATTRIBUTE`.
  - Pros: simple to model in Java as an enum and stable if treated as API
    values.
  - Cons: less natural for URLs/logs and still needs formatting for display.
- Domain action names, such as `entity-created` and `attribute-created`.
  - Pros: describes the original command event clearly.
  - Cons: can be slightly confusing in an undo response unless documented,
    because the actual undo operation is removal.

Chosen decision:

- Use HTTP/API slug names, `create-entity` and `create-attribute`, in backend
  undo responses. They are explicit stable Vedenemo-owned command identifiers
  without tying the API to Java class names.

### Planning Status

All planning questions were resolved before execution.

### Completion Notes

- Promoted the task to `tasks/current-task.md` and executed it.
- Updated shared `azName` validation so `ModelRoot`, `VEntity`, and
  `VAttribute` allow ASCII digits after the first ASCII letter while still
  rejecting leading digits and hyphens.
- Updated CLI `azName` suggestions to preserve digit runs after the suggestion
  has started with an ASCII letter.
- Replaced coarse undo success reporting with richer core-owned `UndoResult`
  metadata containing stable HTTP/API slug command identifiers and target
  fields.
- Updated `POST /sessions/{uuid}/commands/undo` to return operation and target
  details on successful undo.
- Updated CLI undo output to print entity-specific and attribute-specific
  messages.
- Added focused model/core, web API, and CLI tests.
- Updated `docs/cli-reference.md` and `docs/architecture_doc.md`.
- `mvn -B clean verify` passed during implementation.

---

## Add CLI Save And Load For Vedenemo Script Files

Status: executed. Full task text retained here for history.

### Goal

Add `save` and `load` commands to `VedenemoCli` using a new text-based
Vedenemo Script file format with the `.vdos` extension.

`save` should export a selected model from the backend through HTTP, including
the model metadata, full model structure, and executed command history, then
write the result as UTF-8 text to a local file.

`load` should read a `.vdos` file from the local filesystem and send it to the
backend so the backend can recreate the model and command history.

### Recommended Direction

Use a script-like `.vdos` format, not a CLI-private data dump.

The file should be human-readable, UTF-8, and stable enough that users can
inspect and manually edit it when needed. It should also be backend-owned in
meaning: the CLI should move `.vdos` text between local disk and HTTP endpoints,
but should not contain the main replay/import rules.

This is preferable to a CLI-owned data dump because:

- command history already lives in backend session state, not in the CLI;
- future database persistence should happen behind backend adapter/API layers;
- load/replay behavior should be testable without the interactive CLI;
- a script-like format can become the common interchange format for local files,
  backend import/export, and later persistence migrations;
- stable HTTP/API command slugs already exist for undo metadata and can also be
  used as script command names.

The CLI can still own local user interaction:

- model target resolution from current attachment, list number, or `azName`;
- output path prompting and `.vdos` extension handling;
- UTF-8 file read/write;
- friendly messages for missing models, invalid parameters, missing files, and
  backend import/export errors.

### Current Starting Point

Current backend state:

- `ModelRegistry` stores current `ModelRoot` instances.
- `Session` records executed `Command` instances in order.
- `CommandExecutor` can execute and undo command records.
- HTTP currently exposes model listing, entity listing, attribute listing, and
  command execution endpoints.
- HTTP does not yet expose a model export endpoint that combines model metadata,
  nested model structure, and command history.
- HTTP does not yet expose a load/import endpoint.

Current CLI state:

- `VedenemoCliApp` tracks the attached model and latest `list` result.
- `attach [N | azName]` already resolves models by latest list number or
  case-insensitive `azName`.
- `save` can reuse the same target resolution behavior.
- `load` will need path handling and an HTTP client method for import.

### Proposed `.vdos` Shape

The first `.vdos` format should be line-oriented and intentionally simple.

Recommended shape:

```text
vedenemo-script 1

model azName=Example_Model visName="Example Model" version=1.0.0

commands
create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
create-attribute model=Example_Model entity=Customer attribute=Email visName="Email" dataType=TEXT activeSince=1.0.0

snapshot
entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
attribute entity=Customer azName=Email visName="Email" dataType=TEXT activeSince=1.0.0 deprecatedSince=null
```

Guidelines:

- The first line declares the format and version.
- Command names use stable HTTP/API slugs, such as `create-entity` and
  `create-attribute`.
- The command section is authoritative for import/replay.
- The snapshot section records the final model tree and is used for readability
  and validation after replay.
- Values are explicit key/value pairs so ordering is readable and extensions are
  possible.
- Text values are quoted and escaped when needed.
- `visName` values are stored as UTF-8 text.
- Model metadata is explicit.
- Entity and attribute lifecycle version fields are explicit in the script.
- The command list is the replayable source of how the model was built, while
  the snapshot is checked against the replay result.

Recommended initial implementation should include both:

- a model snapshot section for readable current state and validation;
- a command section that is authoritative for replay/import.

That makes the file useful for humans while still keeping command history
available for undo/replay-related development.

Settled format decision details:

- Command-script authoritative means the backend trusts and replays the command
  lines to recreate the model. A snapshot, if present, is only a readable
  comment/validation aid. This keeps one source of truth and fits undo/replay
  semantics well, but the import can only recreate states expressible by known
  commands.
- Snapshot authoritative means the backend trusts the final model tree section
  and command lines are informational history. This can load final state even if
  the command list is incomplete, but it risks drift between the snapshot and
  command history.
- Both command and snapshot sections can be present with commands authoritative:
  import replays commands and then validates that the resulting model matches
  the snapshot. This is stricter and more useful for humans, but requires more
  serializer/parser work in the first implementation.

### Backend / HTTP Scope

Add backend endpoints for export and import instead of requiring the CLI to
compose or interpret the full format.

Recommended endpoints:

```text
GET /models/{modelAzName}/script
POST /models/script
```

Recommended export behavior:

- Resolve `modelAzName` case-insensitively using existing model rules.
- Return `404` if the model is not found.
- Return UTF-8 `text/plain` `.vdos` content.
- Include model metadata, all entities, all attributes, lifecycle version
  fields, and command history for that model.
- Read command history from the model-level command journal, not from the
  current CLI session.

Recommended import behavior:

- Accept UTF-8 `.vdos` text.
- Parse and validate the script server-side.
- Recreate model state through Vedenemo-owned command/application services, not
  by CLI-local object construction.
- Return a structured result with imported model `azName`, command count, and
  whether the model was created, replaced, or rejected.
- If the script model `azName` already exists, reject first with a clear backend
  error. The CLI should then prompt the user and offer to retry with a new
  model `azName`.

Implementation note:

- `vedenemo-core` must remain pure JDK. If a reusable script parser/serializer is
  needed, keep it Vedenemo-owned and JDK-only in an appropriate Vedenemo module.
- `vedenemo-web-api` can handle HTTP content negotiation and DTO mapping.
- Avoid adding JSON/YAML/TOML as the `.vdos` format unless explicitly chosen;
  current direction is a Vedenemo-owned text script.

### CLI `save` Scope

Add command:

```text
save [N | azName] [outputPath]
```

Behavior:

- If an argument is provided, resolve it as a latest-list number or model
  `azName`, following `attach` conventions.
- If no argument is provided, save the currently attached model.
- If no argument and no attached model, print a friendly message and do nothing.
- If the argument is invalid or cannot be resolved, print a friendly message and
  do nothing.
- After resolving the model, request the `.vdos` text from the backend export
  endpoint.
- Suggest output file name `<modelAzName>.vdos` in the CLI start/current working
  directory.
- If an output path is provided inline, use it after extension and overwrite
  handling.
- If no output path is provided inline, let the user accept the default or enter
  another file name/path at the prompt.
- If the user gives no extension, append `.vdos`.
- If the user gives `.vdos`, do not duplicate it.
- Write the file as UTF-8 text.
- Report the saved path and model `azName`.
- Handle write failures gracefully.

Settled save command flow:

- Prompt-only flow:
  - `save`
  - `save Customer_Model`
  - `save 1`
  - After model resolution, CLI prompts:

```text
Output file [Customer_Model.vdos]:
```

  This is simpler interactively and avoids ambiguity, but is weaker for scripts
  and non-interactive usage.

- Inline optional output path flow:
  - `save`
  - `save Customer_Model`
  - `save 1`
  - `save Customer_Model ./exports/customer.vdos`
  - `save 1 ./exports/customer`

  This is better for repeatable shell usage. It requires parsing two optional
  arguments: the first model selector, the second output path. The output path
  should not be treated as a model selector.

- Hybrid flow:
  - Support inline output path when provided.
  - Otherwise prompt with the default `<modelAzName>.vdos`.

  This is the selected flow. It gives good interactive ergonomics while still
  supporting repeatable shell usage.

### CLI `load` Scope

Add command:

```text
load <path>
```

Behavior:

- Accept either a fully qualified path or a path relative to the CLI current
  working directory.
- If no extension is given, append `.vdos` for convenience.
- If the file is not found, print a friendly message and do nothing.
- Read file as UTF-8 text.
- Send the file content to the backend import endpoint.
- Print imported model `azName`, command count, and backend result.
- Automatically attach the CLI session to the imported model after a successful
  load.

### Command History Considerations

This task should explicitly decide where command history comes from.

Recommended:

- Introduce a model-level command journal so backend export is independent of
  CLI session lifetime.
- The backend export endpoint reads command history from the model-level journal
  and serializes only commands belonging to the target model.
- The CLI should not reconstruct command history by comparing model snapshots.
- Loaded commands are treated as persisted baseline state with no undo available
  from the load operation.

Current limitation:

- `Session.commandHistory()` is session-scoped. If multiple sessions can modify
  the same model, a pure session export may not contain the full model command
  history. This task should address that by adding model-level command history
  before relying on `.vdos` export.

### Duplicate / Existing Model Handling

The import path needs a deterministic behavior when the `.vdos` file contains a
model `azName` already present in the backend.

Options:

- reject with a clear message;
- prompt in the CLI to replace;
- import under a new `azName`;
- merge commands into the existing model.

Recommended first version:

- reject first with a clear backend error;
- CLI should then ask whether the user wants to retry import using a new model
  `azName`;
- defer replace and merge until edit and persistence semantics are clearer.

### Resolved Planning Decisions

- `.vdos` should include both command lines and a final model snapshot, with
  command lines authoritative. Import should replay commands and validate the
  resulting model against the snapshot. This makes manual edits slightly harder
  because both sections must stay consistent, but keeps replay semantics clean
  and the file readable.
- Backend export should use a model-level command journal instead of current
  session command history.
- Duplicate model `azName` on load should reject first, then offer the user a
  rename retry flow.
- After successful `load`, CLI should automatically attach to the loaded model.
- Imported command history should be treated as baseline state with no undo
  available from the load operation.
- `save` should use a hybrid output path flow: accept an inline output path when
  provided, otherwise prompt with editable default `<modelAzName>.vdos`.
- `load` should auto-resolve a missing extension by appending `.vdos`.
- Saving over an existing local file should prompt for overwrite confirmation.

### Tests / Verification

At minimum, implementation should add focused tests for:

- `.vdos` serialization includes model metadata, entities, attributes, lifecycle
  fields, and command lines.
- command history export includes only commands for the selected model.
- export of an unknown model returns a clear not-found response.
- import of a valid `.vdos` file creates the model and replays commands.
- import of a missing/invalid script reports a clear error.
- duplicate model import follows the resolved rule.
- CLI `save` with attached model uses the attached model when no argument is
  provided.
- CLI `save N` resolves from the latest model list.
- CLI `save azName` resolves case-insensitively.
- CLI output path handling appends `.vdos` only when needed.
- CLI `load` handles missing files gracefully.
- CLI `load` sends UTF-8 file content to the backend.

At minimum, run:

```bash
mvn -B clean verify
```

If practical, run a local backend plus CLI smoke test:

- create a model with at least one entity and one attribute;
- run `save`;
- verify a `.vdos` file is written as UTF-8 text;
- start a clean backend or use an empty model name;
- run `load`;
- verify the model, entities, attributes, and command count are available.

### Documentation

After implementation:

- Update `docs/cli-reference.md` with `save` and `load` usage.
- Document `.vdos` file naming behavior and extension handling.
- Document the initial `.vdos` text format with a short example.
- Update `docs/architecture_doc.md` if new backend endpoints, script
  serialization components, command history ownership, or load/import flows are
  added.

Before updating `docs/architecture_doc.md`, read and follow
`docs/architecture_doc_instructions.md`.

### Open Questions

No open planning questions remain.

### Completion Notes

- Promoted the task to `tasks/current-task.md` and executed it.
- Added backend-owned `.vdos` script import/export support in core.
- Added model-level command journaling for model-targeting commands.
- Added backend HTTP script export/import endpoints.
- Added CLI `save [N | azName] [outputPath]` and `load <path>`.
- Implemented UTF-8 file I/O, `.vdos` extension handling, overwrite
  confirmation, duplicate-load rename retry, and auto-attach after load.
- Treated loaded commands as baseline state with no current-session undo stack
  entries.
- Updated CLI and architecture documentation.
- Added focused core, web API, and CLI tests.
- `mvn -B clean verify` passed during implementation.

## Add UX Model Selector And Refresh

Status: executed. Short historical task entry added after implementation.

### Goal

Add a UX control for selecting the active model from backend data and refreshing
the available model list.

### Completion Notes

- Added a model dropdown populated from the backend at page load.
- Added a `Refresh model list` action to reload available models.
- Changed the model selector label to `Select model`.
- Committed as `34f4c1f Add UX model selector with refresh`.

## Add UX Model Event Connection And PlantUML Text Output

Status: executed. Short historical task entry added after implementation.

### Goal

Allow the UX to connect to backend model-change events and show the selected
model as a PlantUML class-diagram text representation.

### Completion Notes

- Added backend `/models/events` WebSocket support through an adapter layer.
- Added a UX `Connect` / `Disconnect` toggle for listening to model changes.
- Added `PlantUmlModelAdapter` to transform `VEntity` values to PlantUML
  classes and `VAttribute` values to class attributes.
- Initially displayed the generated PlantUML as plain ASCII text.
- Committed as `993bc5d Add UX model event connection and PlantUML text`.

## Render PlantUML Diagrams Visually In UX

Status: executed. Short historical task entry added after implementation.

### Goal

Replace plain PlantUML text output with visual, scrollable diagram rendering in
the UX.

### Completion Notes

- Added `@plantuml/core` to render PlantUML diagrams in the browser.
- Added `PlantUmlDiagramRendererAdapter`.
- Replaced the text area with a scrollable visual diagram viewport.
- Kept the renderer lazy-loaded so the initial UX bundle remains smaller.
- Fixed browser rendering follow-ups:
  - switched from `renderToString` to DOM rendering with completion detection;
  - loaded `viz-global.js` as a classic browser script asset before importing
    `@plantuml/core`.
- Committed as:
  - `8a9d1ab Render PlantUML diagrams visually in UX`
  - `66f4e4a Fix UX PlantUML diagram rendering`
  - `6741247 Load PlantUML Graphviz dependency as browser script`

## Simplify UX PlantUML Model Content

Status: executed. Short historical task entry added after implementation.

### Goal

Keep the visual model diagram focused on user-authored model content instead of
internal metadata.

### Completion Notes

- Removed entity `azName`, `activeSince`, and `deprecatedSince` rows from
  generated class bodies.
- Kept entity `azName` only as the internal PlantUML identifier needed for
  stable rendering.
- Rendered attribute rows with attribute `visName` instead of `azName`.
- Kept the attribute data type visible.
- Committed as `c0a33ae Simplify UX PlantUML model content`.

## Hide PlantUML Class-Specific Diagram Chrome

Status: executed. Short historical task entry added after implementation.

### Goal

Keep using PlantUML class-box layout while avoiding visual markers that imply
Vedenemo entities are implementation classes.

### Completion Notes

- Added `hide circle` to remove the class-specific `C` marker.
- Added `hide empty members` to suppress empty member compartments and their
  separator lines while keeping real attribute rows visible.
- Committed as:
  - `9c288d7 Hide class marker in UX PlantUML diagrams`
  - `d301de7 Hide empty PlantUML member compartments`

## Refactor CLI For Shared Terminal And Web Console Use

Status: executed.

### Goal

Refactor the current `VedenemoCli` command handling so the same command behavior
can be used from two frontends:

- the existing terminal CLI;
- a virtual CLI exposed in the UX at `/console`.

The web console should feel like a CLI session in the browser, but it must not
run the Java CLI process or duplicate CLI command behavior in TypeScript. Common
command parsing, session-oriented command execution, and user-facing output
formatting should be Vedenemo-owned Java code that can be used by both terminal
and web entry points.

### Architecture Direction

Recommended implementation direction:

- Extract reusable CLI behavior from `vedenemo-cli` into a Vedenemo-owned Java
  component.
- Keep terminal-specific concerns in the terminal CLI:
  - stdin/stdout loop;
  - local filesystem access;
  - interactive prompts tied to terminal input.
- Keep web-console-specific concerns in the web API / UX:
  - HTTP request/response lifecycle;
  - browser console session identity;
  - browser UI rendering and command input history.
- Do not make the browser spawn or talk to a Java CLI process.
- Do not add dependencies from `vedenemo-core` to CLI, web API, UX, or adapter
  modules.

Resolved implementation placement decision:

- Use Option B: introduce a new Vedenemo-owned module such as
  `vedenemo-cli-api` / `vedenemo-command-console` for shared command behavior,
  used by both `vedenemo-cli` and `vedenemo-web-api`.

This avoids making the web API depend on terminal CLI packaging and keeps the
module name aligned with the shared responsibility.

### Shared Command Behavior

The shared component should own:

- command-line parsing for existing CLI commands;
- command dispatch;
- command result objects or rendered output lines;
- active session/model context used by CLI-like workflows;
- common backend HTTP client behavior if current CLI remains an HTTP client.

The shared component should not directly own:

- terminal input/output;
- browser UI rendering;
- local file picker or browser file APIs;
- local filesystem access unless abstracted behind a capability interface.

### Capability Differences

The terminal CLI has local filesystem capability. The web console does not.

For the first version, the virtual CLI must reject filesystem-dependent
commands with a clear message:

```text
Command 'save' is not supported in the web console because it requires local file access.
Command 'load' is not supported in the web console because it requires local file access.
```

The shared command behavior should make this explicit through a capability or
execution-context check instead of ad hoc command-name checks in the UX.

Resolved unsupported-command behavior:

- For the first version, unsupported `save` and `load` in the web console return
  plain text output only. No separate structured unsupported-capability status is
  needed yet.

Future work can later add browser upload/download workflows for `.vdos` files,
but that is intentionally out of scope for this first web console task.

### Web API Scope

Add backend endpoints for browser console sessions. Exact route names can be
adjusted during implementation, but the first version should likely include:

```text
POST /console/sessions
POST /console/sessions/{sessionId}/commands
DELETE /console/sessions/{sessionId}
```

Behavior:

- `POST /console/sessions` starts a CLI-like web console session and returns a
  console session id.
- `POST /console/sessions/{sessionId}/commands` executes one submitted command
  line and returns command output and status.
- `DELETE /console/sessions/{sessionId}` ends the console session.
- Invalid/missing session ids return clear client errors.
- Command failures should return structured status and readable output.

Resolved session design:

- Use a console-session wrapper with its own browser-facing console session id.
- The console-session wrapper internally owns or links a backend model-editing
  session.
- Do not directly expose generic backend edit session ids as the browser console
  session identity.

Elaboration:

- Directly exposing an existing backend edit session id would make the browser
  console thinner, but it also means browser UI state, CLI-like last-list
  numbering, attached model context, and future console-specific capability
  flags all live directly on the same session concept used by non-console API
  clients.
- A console-session wrapper gives the web console its own session id and
  lifecycle. Internally it can create, own, or link an existing backend edit
  session. This keeps web-console behavior isolated while still reusing the
  existing model-editing session behavior under the hood.
- The wrapper approach is more explicit if later the web console needs command
  history display, input history, capability flags, default attached model
  behavior, or browser-specific output shaping.

Resolved initial model-binding behavior:

- When opening `/console`, the UX should pass the currently connected model
  `azName` if the UX is connected to model change events for a selected model.
- The web console session should automatically attach/bind to that connected
  model at session start.
- If the UX is not connected to any model, the web console session starts with
  no attached model.
- The first version should not auto-attach merely because a model is selected in
  the dropdown; it should only auto-attach when there is an active model
  connection.

### UX Scope

Add a `/console` route or subpath to the Vite UX.

First version behavior:

- use a separate full-page console view rather than sharing the existing model
  selector layout;
- show a terminal-like command history area;
- show an input for one command at a time;
- submit commands to the backend console command endpoint;
- append command output to the history;
- keep enough browser-side state for display and command input history;
- start a console session when the page opens;
- end or abandon the console session gracefully when possible.

The `/console` page does not need to support:

- local `.vdos` save/load;
- terminal emulation beyond the basic command/output interaction;
- streaming output;
- WebSocket command execution.

HTTP request/response is sufficient for the first implementation.

### Tests / Verification

At minimum, implementation should add tests for:

- shared command behavior executes representative existing commands consistently
  for terminal and web-console use;
- virtual CLI rejects `save` and `load` with the agreed unsupported message;
- web console session creation succeeds;
- executing a command through a web console session returns readable output;
- invalid console session id returns a clear client error;
- UX build succeeds.

At minimum, run:

```bash
mvn -B clean verify
cd vedenemo-ux
npm run build
```

### Documentation

After implementation:

- Update `docs/cli-reference.md` if command behavior or wording changes.
- Update `README.md` if local UX usage now includes `/console`.
- Update `docs/architecture_doc.md` because this changes CLI/web API/UX runtime
  structure and introduces a new shared command-flow boundary.

Before updating `docs/architecture_doc.md`, read and follow
`docs/architecture_doc_instructions.md`.

### Open Questions

No open planning questions remain.

### Completion Notes

- Added `vedenemo-command-console` as the shared Java command-flow module used
  by both terminal CLI adapters and the web API virtual console.
- Refactored terminal CLI DTO/client interfaces into the shared module while
  keeping terminal stdin/stdout and local `.vdos` file access in
  `vedenemo-cli`.
- Added browser console-session HTTP endpoints:

```text
POST /console/sessions
POST /console/sessions/{sessionId}/commands
DELETE /console/sessions/{sessionId}
```

- Added a browser-facing console-session wrapper id that owns an internal
  backend edit session id.
- Added `/console` as a separate full-page UX console with command history and
  one-command-at-a-time execution through HTTP.
- The main UX passes the actively connected model `azName` to `/console`; when
  no model connection is active, the console starts unattached.
- Web console `save` and `load` return the agreed plain text unsupported local
  file access messages.
- Added focused shared-console and web API tests.
- `npm run build` in `vedenemo-ux` passed.
- `mvn -B clean verify` passed.

## Align Terminal And Browser Console CLI Command Coverage

Status: executed.

### Goal

Make the browser `/console` virtual CLI functionally match the terminal
`VedenemoCli` command surface except for local filesystem commands.

The intended difference is:

- terminal CLI supports `save`, `snapshots`, and `load` because it has local
  filesystem access;
- browser virtual console rejects filesystem-dependent commands clearly;
- all other model-authoring and inspection commands should be available through
  both command surfaces with equivalent prompts, output, context handling, undo,
  and validation behavior.

### Current Problem

The previous implementation did not meet that contract. `VedenemoCliApp`
contained terminal-only interactive prompt flows for:

- `add`
- `attr add`
- `assoc add`
- `assoc add ownership`
- `assoc add reference`
- `assoc add relation`

The shared `vedenemo-command-console` `ConsoleSession` used by the browser
virtual console rejected `add`, `attr`, and `assoc` with:

```text
Command '<command>' requires interactive terminal prompts and is not supported in the web console yet.
```

Its `help` output also omitted terminal-supported authoring commands such as
`add`, `attr add`, and `assoc add [ownership | reference | relation]`, while it
listed unsupported `save` and `load`.

### Scope

- Move or model interactive prompt workflows in shared command-console behavior
  so both terminal CLI and browser virtual console can run the same authoring
  flows.
- Keep terminal stdin/stdout handling in `vedenemo-cli`.
- Keep browser UI and HTTP request/response handling in `vedenemo-ux` and
  `vedenemo-web-api`.
- Add a prompt-state protocol for browser console sessions if a command needs
  several user inputs across separate HTTP requests.
- Preserve numbered shortcut behavior for association kind selection:
  `1 ownership`, `2 reference`, `3 relation`.
- Preserve Esc cancellation semantics:
  - terminal CLI Esc cancels the active prompt flow;
  - browser virtual console Esc cancels the active prompt/input flow.
- Keep `save`, `snapshots`, and `load` terminal-only unless a later task adds
  browser file upload/download support.
- Update `help` output so browser virtual console shows the same supported
  non-file commands as terminal CLI and clearly marks file commands as
  unsupported.

### Architecture Direction

Prefer extending `vedenemo-command-console` as the owner of CLI-like command
semantics. The shared module should own command flow state, prompt text,
input validation, command dispatch, and output formatting. Entry points should
provide capability-specific I/O:

- terminal CLI supplies blocking line input and local filesystem access;
- browser console supplies one submitted line at a time over HTTP and no local
  filesystem access.

Do not duplicate terminal prompt logic in TypeScript. Do not make
`vedenemo-web-api` depend on `vedenemo-cli`.

### Tests / Verification

At minimum, implementation should add focused coverage for:

- shared console `help` lists terminal-equivalent non-file commands;
- browser console can create a model through `add`;
- browser console can create an entity through `add` when a model is attached;
- browser console can create an attribute through `attr add`;
- browser console can create ownership/reference/relation associations through
  `assoc add`;
- browser console Esc cancellation abandons a partially entered prompt flow
  without executing a command;
- terminal CLI still supports the same prompt flows after refactoring;
- browser console still rejects `save`, `snapshots`, and `load` as
  filesystem-dependent commands;
- `mvn -B verify` passes;
- `npm run build` passes.

### Completion Notes

- Extended `vedenemo-command-console` `ConsoleSession` with browser-compatible
  prompt-flow state for `add`, `attr add`, and `assoc add`.
- Browser console can now create models, entities, attributes, directed
  ownership/reference associations, and bidirectional relations through the
  same command words as terminal CLI.
- Browser console `help` now lists the supported non-file authoring commands
  and marks `save`, `snapshots`, and `load` as unsupported file-access
  commands.
- Browser console Esc cancellation now sends cancellation to the backend so
  pending prompt state is abandoned server-side.
- Browser console blank Enter can be submitted while a prompt flow is active,
  allowing default prompt values to be accepted.
- Added focused shared-console and web API tests.
- `mvn -B verify` passed.
- `npm run build` passed in `vedenemo-ux`.

## Plan Cloud Snapshot Storage For Browser Console Save/Load

Status: executed.

### Goal

Define a storage-adapter direction that lets the browser virtual console save
and load `.vdos` snapshots through backend-managed cloud storage, while
preserving the option to reuse the same abstraction or selected backend for
future model-instance persistence.

This started as a planning/pondering item. The first implementation keeps the
terminal filesystem save/load behavior intact and adds backend-managed cloud
snapshots for browser console sessions.

### Current Problem

The terminal CLI can run:

- `save`
- `snapshots`
- `load`

because it has local filesystem access and can read/write `.vdos` files under
`.vedenemo`.

The browser `/console` intentionally rejects those commands because browser
console sessions submit one command or prompt answer at a time over HTTP and do
not have access to the user's local filesystem.

To support browser save/load without introducing browser file uploads as the
primary workflow, the backend needs a cloud snapshot store behind a
Vedenemo-owned storage interface.

### Architectural Direction

Introduce a Vedenemo-owned snapshot storage port before choosing any concrete
cloud product. The port should be generic enough for storing named byte/text
documents with metadata, but narrow enough that the first implementation is not
a general database abstraction.

Possible shape:

```text
SnapshotStore
  listSnapshots(scope)
  readSnapshot(scope, snapshotKey)
  writeSnapshot(scope, snapshotKey, content, metadata, overwritePolicy)
  deleteSnapshot(scope, snapshotKey)
```

The first stored content can be plain UTF-8 `.vdos` text. The storage record
metadata should include at least:

- model `azName`
- model visible name
- model version
- command count, if cheaply available
- creation/update timestamp
- content type or format version, such as `vedenemo-script 1`
- optional owner/workspace scope placeholder, even before authentication exists
- model last modification timestamp, once the model layer exposes one

Keep cloud SDK dependencies out of `vedenemo-core` and
`vedenemo-model-api`. Put storage ports in an appropriate Vedenemo-owned SPI
module and concrete GCP adapters in adapter/infrastructure modules. Application
composition should wire the selected adapter explicitly.

### Selected GCP Storage Direction

Use Google Cloud Storage buckets and store each `.vdos` snapshot as an object.

Good fit for the first snapshot task because `.vdos` is already a file-like
artifact. Object names can encode scope, model identity, and a user-chosen
snapshot name, for example:

```text
snapshots/{scope}/{modelAzName}/{snapshotName}.vdos
```

Reasons:

- Closest match to saving/loading `.vdos` files.
- Simple mental model: object data plus object metadata.
- Easy to retain multiple historical snapshots.
- Good stepping stone before designing model-instance persistence.
- Does not force Vedenemo model structures into a cloud database schema too
  early.

Known limitations:

- Listing/filtering by arbitrary metadata is limited compared to a database.
- Concurrent writes need explicit generation/precondition handling.
- Not ideal as the primary store for highly queryable model instances.

Use this for the near-term goal: browser `save`, `snapshots`, and `load` for
whole model scripts.

Do not make this first storage port the final model-instance persistence API.
Instead, treat it as a durable artifact store that can later coexist with a
separate `ModelInstanceStore` or event/journal store.

### Snapshot Naming And Overwrite Semantics

The first cloud snapshot implementation should not introduce automatic version
control. Snapshot names can be manually chosen by the user. A save to the same
snapshot name overwrites the previous stored `.vdos` snapshot after normal
overwrite confirmation.

Recommended first object naming shape:

```text
snapshots/{scope}/{modelAzName}/{snapshotName}.vdos
```

The latest save timestamp remains important even without snapshot versioning.
Both local file-based snapshots and cloud snapshots should eventually carry
enough metadata to answer:

- when the model was last modified in memory;
- when the snapshot was last saved;
- which model modification timestamp was captured by this snapshot.

This implies adding model-level modification metadata before or alongside cloud
snapshot overwrite handling. At minimum, `ModelRoot` or model metadata should
track a last modification timestamp that changes when model-authoring commands
successfully mutate the model. `.vdos` export should include that timestamp in
the model metadata, and `.vdos` import should restore or validate it
deterministically.

Overwrite confirmation should use this metadata:

- If the target snapshot does not exist, save normally.
- If the target snapshot exists and its saved model modification timestamp is
  older than or equal to the current model's last modification timestamp, a
  normal overwrite confirmation is enough.
- If the target snapshot exists and its saved model modification timestamp is
  newer than the current model's last modification timestamp, warn that the
  user is about to overwrite a snapshot that appears to contain newer model
  changes than the currently loaded model.

The same stale-overwrite warning should apply to terminal `.vedenemo` files and
cloud-backed snapshots once both paths have access to the metadata. Before the
metadata exists, implementation can keep the existing simple overwrite prompt.

### Authentication And Authorization Direction

Do not put a Google Cloud API key, service account key, signed URL, or bucket
credential in the browser UX.

For the first development-phase implementation, keep the same trust boundary
that Vedenemo already uses:

- the browser talks only to the Vedenemo backend;
- the backend authenticates to Google Cloud using its runtime identity or
  Application Default Credentials;
- the GCP service account has narrowly scoped bucket permissions;
- browser console `save`, `snapshots`, and `load` call Vedenemo HTTP endpoints
  rather than Cloud Storage directly.

This means Vedenemo can defer end-user authentication/authorization for the
first private Tailscale-hosted development slice, but the backend API should be
designed so authorization can be added without changing storage adapters.

Minimum development-phase controls:

- keep the backend reachable only inside the private network;
- use a private bucket with no public object access;
- use a dedicated service account for snapshot storage;
- grant only the bucket/object permissions needed for snapshot read/write/list;
- include a placeholder storage scope in the `SnapshotStore` API, even if it is
  initially a fixed development scope;
- avoid exposing raw object names that would become authorization-sensitive
  later unless they are treated as opaque snapshot keys.

API keys are not a good fit for this backend storage access. They identify a
calling project/application, but they are not an authorization model for
per-object user access and should not be used as a browser-visible storage
secret. Service account credentials also must not be shipped to the browser.

Signed URLs are useful later for direct browser upload/download of specific
objects, but they are not needed for the first console command flow. If used
later, signed URLs should be short-lived, generated by the backend after
Vedenemo authorization checks, and scoped to one object/action.

### Manual GCP Setup Checklist

The repository already has GCP infrastructure under `infra/gcp`, including
`infra/gcp/firebase-hosting` for Firebase Hosting setup. New cloud snapshot
setup files should follow that convention, for example:

```text
infra/gcp/cloud-storage-snapshots
```

An initial template scaffold now exists in that directory with Terraform files,
manual/setup runbooks, and shell script templates for API bootstrap, backend
environment output, and access verification.

The scaffold files should be treated as the starting point for implementation
setup:

- `README.md` summarizes the chosen first-slice decisions and local tool
  requirements.
- `MANUAL-PHASES.md` records the manual project, billing, auth, review, budget,
  and verification phases.
- `RUNBOOK.md` interleaves those manual phases with the concrete bootstrap,
  Terraform, output, and verification commands.
- `terraform.tfvars.example` is the committed placeholder for missing project,
  bucket, prefix, region, scope, service account, and retention values; real
  `.tfvars` files remain ignored.
- `scripts/bootstrap-apis.sh` corresponds to the API-enable step after manual
  project and billing selection.
- `scripts/print-backend-env.sh` corresponds to copying Terraform outputs into
  backend deployment configuration.
- `scripts/verify-snapshot-access.sh` corresponds to the final storage
  read/write/list verification phase.

Prefer repeatable Terraform and small shell helper scripts in that directory
over browser-based Google Cloud Console setup. The scripts/module should encode
the chosen services, bucket settings, service account, IAM bindings, and
documented outputs where practical. Browser console setup should be reserved for
one-time project prerequisites that cannot be managed reliably from Terraform or
`gcloud` in this repository.

The setup documentation should be an interleaved manual/scripted runbook rather
than one large manual checklist followed by one opaque script. A reasonable
first shape is:

1. Manually choose or create the GCP project and confirm billing ownership.
2. Manually authenticate locally with `gcloud` using an account allowed to
   manage services, buckets, service accounts, IAM, and budgets.
3. Run the repo-managed bootstrap command from
   `infra/gcp/cloud-storage-snapshots` to enable required APIs that Terraform
   can manage safely.
4. Review and edit Terraform variables for project ID, bucket name, region,
   object prefix, service account name, and initial storage scope.
5. Run `terraform init` and `terraform plan` from the snapshot infra directory.
6. Manually review the plan for public access settings, IAM grants, service
   account names, retention choices, and cost-sensitive resources.
7. Run `terraform apply` only after the plan matches the intended private
   development setup.
8. Copy Terraform outputs into backend deployment configuration or local
   environment files that are intentionally outside version control.
9. Manually configure any budget alert or organization-level policy that is not
   handled by the infrastructure module.
10. Run a small verification command from the infra directory or backend tests
    to confirm that the configured backend identity can list, read, and write
    only under the intended snapshot prefix.

Before implementation starts, make the following configuration choices and
record the selected values in deployment notes, Terraform variables, script
arguments, or environment configuration:

1. Choose the GCP project that owns snapshot storage.
2. Enable billing for the project if it is not already enabled.
3. Enable the Cloud Storage API if the project does not already use it. Prefer
   doing this from the infrastructure module.
4. Choose the first bucket name, region, and storage class.
5. Create one private bucket for Vedenemo snapshots from the infrastructure
   module.
6. Keep public access prevention enabled unless a later public artifact task
   explicitly changes that.
7. Decide the object prefix convention, for example:

```text
snapshots/dev/{modelAzName}/{snapshotName}.vdos
```

8. Decide the first fixed storage scope, for example `dev`, `single-user`, or
   a deployment name.
9. Create or select the service account that the backend will use for snapshot
   storage. Prefer managing this in infrastructure code.
10. Grant that service account only the required bucket permissions from the
   infrastructure module.
11. Decide how the deployed backend receives Google credentials.
12. Decide how local development receives Google credentials.
13. Configure backend environment variables:

```text
VEDENEMO_SNAPSHOT_STORE=gcs
VEDENEMO_GCS_PROJECT_ID=<project-id>
VEDENEMO_GCS_BUCKET=<bucket-name>
VEDENEMO_GCS_PREFIX=snapshots/dev
VEDENEMO_SNAPSHOT_SCOPE=dev
```

14. Configure a GCP budget alert for the project. Automate this if it fits the
   current infra pattern; otherwise document the manual step.
15. Decide first retention behavior: maximum snapshots per model, maximum age,
   or no automatic retention for the first private slice.
16. Use user-chosen snapshot names. Saving the same name overwrites the old
   snapshot after confirmation.
17. Plan model last-modification timestamp metadata so overwrite confirmation
   can warn when the existing snapshot appears newer than the current model.
18. Document how to rotate the service account or deployment identity if
   credentials are suspected to be exposed.

The first infrastructure slice should produce enough outputs for backend
configuration without requiring users to search the Cloud Console manually:

- GCP project ID;
- bucket name;
- object prefix;
- backend service account email;
- any Workload Identity Federation provider or principal identifiers if the
  backend deployment uses keyless authentication;
- the exact environment variable values needed by `vedenemo-web-api`.

The deployed-backend credential decision deserves special care:

- If the backend runs on a GCP runtime such as Cloud Run, prefer attaching a
  dedicated runtime service account to the service. The application then uses
  Google client libraries with Application Default Credentials. No JSON key
  file is stored in the repository, copied into the image, or exposed to the
  browser.
- If the backend runs on a non-GCP host, such as the current private Tailscale
  machine, prefer Workload Identity Federation if practical. If that is too
  much setup for the first development slice, use a user-managed service
  account key only as a temporary development/deployment secret. Store it
  outside the repository, inject it through the host's secret/environment
  mechanism, restrict its bucket permissions, and plan to replace it later.
- For local development, use `gcloud auth application-default login` or
  service account impersonation so ADC can find credentials on the developer
  machine. Avoid committing credential files or requiring developers to paste
  long-lived keys into config files.
- In all cases, the browser should never receive GCP credentials. Browser
  console commands call the Vedenemo backend, and the backend performs storage
  operations after applying Vedenemo-side capability/authorization checks.

### Billing And Cost Controls

Cloud snapshot storage introduces real billing even when the data is small.
The first implementation should include basic cost guardrails rather than
assuming `.vdos` files are always negligible.

Cost sources to account for:

- stored object bytes;
- object write/list/read operations;
- network egress when snapshots are downloaded;
- retrieval fees if a non-Standard storage class is chosen;
- extra costs if dual-region/multi-region replication or lifecycle features are
  enabled.

Recommended first-phase controls:

- use one Standard storage bucket in one region unless there is a clear
  deployment reason for multi-region;
- cap snapshot size at the Vedenemo API boundary;
- cap snapshots per scope/model, or implement retention by count/age;
- make manual-name overwrite behavior explicit;
- keep object versioning off until there is a retention requirement;
- configure budget alerts in the GCP project;
- document expected operation volume for `save`, `snapshots`, and `load`;
- prefer backend-mediated save/load over signed URL transfers until direct
  transfer is needed.

Billing should be part of the implementation acceptance criteria:

- failed storage writes must produce clear user-facing errors;
- object names should be deterministic enough to clean up;
- tests should cover overwrite/duplicate behavior without relying on live GCP;
- production deployment must document the required bucket, service account, and
  IAM permissions.

### Future Model-Instance Persistence Considerations

Model-instance persistence is a larger problem than `.vdos` snapshot storage.
It may need:

- identity rules for instances;
- schema evolution when metamodels change;
- validation against model versions;
- query patterns over instances;
- transactions across related instances;
- history/audit/event storage;
- access control and sharing;
- import/export between durable storage and `.vdos`.

Do not compare or select model-instance persistence backends in this snapshot
task. Revisit those alternatives only after Vedenemo has a concrete model
instance design and real instance query/update requirements. Cloud Storage
remains useful for exported snapshots, backups, and archive artifacts even if
live instances later use a different persistence backend.

The snapshot-store task should therefore avoid claiming that Cloud Storage is
the final persistence answer. It should create a small cloud save/load slice and
generate practical learning about auth, deployment, naming, metadata, and
operational handling.

### Possible First Implementation Slice

- Add a pure Vedenemo storage SPI for snapshot artifacts.
- Add a local filesystem adapter for tests/dev parity if useful.
- Add a GCP Cloud Storage adapter in a separate adapter module.
- Wire the selected snapshot store in application composition.
- Add backend endpoints for browser-console snapshot operations, for example:

```text
GET  /snapshots
POST /snapshots/{modelAzName}
GET  /snapshots/{snapshotKey}
POST /models/script/from-snapshot
```

- Extend browser console capabilities so:
  - `save` writes the attached model's exported `.vdos` to cloud storage;
  - `snapshots` lists cloud snapshots available in the configured scope;
  - `load <snapshot-number | snapshot-key>` imports the chosen stored `.vdos`;
  - terminal CLI keeps plain `save`, `snapshots`, and `load` commands backed by
    the local filesystem while browser console uses the same plain command
    names backed by cloud snapshots.

### Implementation Result

- Added `SnapshotStore`, `SnapshotDescriptor`, and `SnapshotContent` to
  `vedenemo-core-spi`.
- Added `vedenemo-storage-gcs` with `GcsSnapshotStore`.
- Wired browser console snapshot storage in `vedenemo-web-api` from:
  - `VEDENEMO_SNAPSHOT_STORE=gcs`
  - `VEDENEMO_GCS_PROJECT_ID`
  - `VEDENEMO_GCS_BUCKET`
  - `VEDENEMO_GCS_PREFIX`
  - `VEDENEMO_SNAPSHOT_SCOPE`
- Kept terminal `VedenemoCli` `save`, `snapshots`, and `load` local
  filesystem-backed.
- Implemented browser `/console` plain `save`, `snapshots`, and `load` through
  the existing console-session command endpoint instead of adding separate
  snapshot REST endpoints in this slice.
- Added deterministic tests using in-memory fake snapshot storage rather than
  live GCP.

### Resolved Auth Boundary For First Slice

For the first private development slice, private Tailscale/backend reachability
is an acceptable access boundary. Anyone who can reach the Vedenemo backend in
that private network can use the snapshot endpoints.

Consequences of this decision:

- Do not require per-user authentication or sharing rules in the first cloud
  snapshot implementation.
- Do not require an extra shared development token for the first slice unless a
  later deployment constraint makes it necessary.
- Keep snapshot access behind the Vedenemo backend. Browser clients must not
  receive Google Cloud API keys, service account keys, signed URLs, or other
  direct Cloud Storage credentials.
- Limit the backend service account with GCP IAM to only the chosen
  bucket/prefix and required object operations.
- Treat snapshot keys as backend-owned identifiers so later user/workspace
  authorization can be added without changing command syntax.
- Document explicitly that this private development mode does not provide
  per-user snapshot privacy or sharing guarantees.

### Resolved First Slice Decisions

- Use one global bucket namespace for the first phase.
- Use the backend server clock as the model last-modification timestamp source.
- Browser console cloud `load` should prompt for a replacement model `azName`
  when the loaded `.vdos` model `azName` already exists, matching terminal CLI
  behavior.
- Keep command names plain and context-specific:
  - terminal CLI `save`, `snapshots`, and `load` use the local filesystem;
  - browser console `save`, `snapshots`, and `load` use cloud snapshots.
- Create a Vedenemo-specific snapshot store, not a generic artifact store.
