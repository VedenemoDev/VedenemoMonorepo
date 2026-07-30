# Current Task

## Implement Dynamic Model Instance Data API With Multiple Roots

Status: executed.

### Goal

Add the first process-local model-instance capability so HTTP clients can create
and query runtime data records whose fields are validated against a loaded
Vedenemo model definition.

### Scope

- Add pure JDK instance-data types and validation/service behavior in
  `vedenemo-core`.
- Keep instance data process-local and in memory.
- Bind instance datasets by model `azName` plus backend-assigned globally
  unique `instanceRootId`, and record the model version visible when each root
  is created.
- Validate entity instance values against loaded `ModelRoot` entity attributes
  and `DataType`.
- Store association instance links separately from scalar attribute maps.
- Validate association links against modeled association endpoints.
- Add static `/data` HTTP routes in `vedenemo-web-api` that resolve dynamic
  `modelAzName`, `entityAzName`, and `associationAzName` path parameters at
  request time.
- Keep JSON DTO parsing/serialization in `vedenemo-web-api`.
- Keep the first slice HTTP-only; terminal and browser CLI commands remain
  model-authoring and maintenance oriented.

### Implemented API

```text
GET  /data/{modelAzName}/_api
GET  /data/{modelAzName}/roots
POST /data/{modelAzName}/roots
GET  /data/{modelAzName}/roots/{instanceRootId}
PUT  /data/{modelAzName}/roots/{instanceRootId}
GET  /data/{modelAzName}/roots/{instanceRootId}/_api
POST /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}
GET  /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}
GET  /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_count
GET  /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/{instanceId}
POST /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_query
POST /data/{modelAzName}/roots/{instanceRootId}/_links/{associationAzName}
GET  /data/{modelAzName}/roots/{instanceRootId}/_links/{associationAzName}
```

### Completion Notes

- Added `InstanceId`, `InstanceValue`, `EntityInstance`,
  `AssociationInstanceLink`, `ModelInstanceDataset`, `ModelInstanceRegistry`,
  `RelationshipDirection`, `RelationshipPredicate`, `EntityInstanceQuery`, and
  `ModelInstanceService`.
- Added `InstanceDataResource` and wired it into the Javalin web API runtime.
- Implemented `TEXT`, `NUMERIC`, `URL`, and `DATA` validation for instance
  values.
- Implemented deterministic entity instance listing and exact-match filters.
- Implemented entity-instance count reads for UX grouped instance summaries.
- Implemented process-local model-instance root metadata with globally unique
  root ids, optional visual aliases, backend create/list/read/rename endpoints,
  and root-scoped instance-data routes.
- Implemented multiple isolated model-instance roots per loaded model.
- Implemented one-hop relationship `_query` predicates that match related
  entity attributes through stored association links.
- Added focused core and web API tests.
- Updated `ConsoleResourceTest` construction for no-store assertions to avoid
  ambient `VEDENEMO_SNAPSHOT_STORE` environment coupling.
