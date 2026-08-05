# Current Task

## Add Model Instance API Docs UX Route

Status: executed.

### Goal

Add a new UX route at `/modelInstanceApi` that shows Swagger-like HTTP API
documentation for the currently selected model-instance root. The selected
target is chosen from the `Model instances` tab in the main UX view through a
root-node `API docs...` menu action.

### Scope

- Add a new frontend route/page for `/modelInstanceApi`.
- Add `API docs...` to the existing model-instance root action menu in the
  `Model instances` tab.
- Open the docs route in a new browser tab with `modelAzName` and
  `instanceRootId` URL query parameters.
- Load root-scoped API metadata from
  `/data/{modelAzName}/roots/{instanceRootId}/_api`.
- Load model-instance root metadata from
  `/data/{modelAzName}/roots/{instanceRootId}`.
- Render a Vedenemo-native Swagger-like read-only documentation view.
- Document only root-scoped instance-data routes.
- Include entity-specific operations and association-link operations derived
  from the selected model metadata.
- Show method, resolved path, purpose, request body example, and response
  example for each documented operation.
- Generate request and response examples from model metadata.
- Keep interactive `try it` request execution controls out of scope.

### Completion Notes

- Added frontend metadata types for entity operations, entity body examples,
  association link operations, association body examples, and model version.
- Added `/modelInstanceApi` route handling in `vedenemo-ux`.
- Added a root-scoped API metadata fetcher for
  `/data/{modelAzName}/roots/{instanceRootId}/_api`.
- Added `API docs...` to the model-instance root action menu and opened it in a
  new browser tab.
- Added a read-only documentation page that identifies the selected model and
  model-instance root.
- Rendered entity sections with modeled attributes and documented create, list,
  read, update, query, and count operations from backend-owned `_api` metadata.
- Rendered association sections with source/target metadata and documented link
  create/list operations from backend-owned `_api` metadata.
- Added generated JSON request and response examples without adding an
  OpenAPI-compatible backend document or Swagger UI dependency.
- Updated `docs/architecture_doc.md` to reflect the concrete `/modelInstanceApi`
  frontend route and metadata flow.
- Marked the corresponding backlog item executed while keeping it in
  `tasks/backlog.md` as history.
