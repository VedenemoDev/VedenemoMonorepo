# Current Task

## Implement Dynamic Model Instance Data API

Status: executed.

### Goal

Add the first process-local model-instance capability so HTTP clients can create
and query runtime data records whose fields are validated against a loaded
Vedenemo model definition.

### Scope

- Add pure JDK instance-data types and validation/service behavior in
  `vedenemo-core`.
- Keep instance data process-local and in memory.
- Bind instance datasets by model `azName` and record the model version visible
  when the dataset is created.
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
POST /data/{modelAzName}/{entityAzName}
GET  /data/{modelAzName}/{entityAzName}
GET  /data/{modelAzName}/{entityAzName}/{instanceId}
POST /data/{modelAzName}/{entityAzName}/_query
POST /data/{modelAzName}/_links/{associationAzName}
GET  /data/{modelAzName}/_links/{associationAzName}
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
- Implemented one-hop relationship `_query` predicates that match related
  entity attributes through stored association links.
- Added focused core and web API tests.
- Updated `ConsoleResourceTest` construction for no-store assertions to avoid
  ambient `VEDENEMO_SNAPSHOT_STORE` environment coupling.
