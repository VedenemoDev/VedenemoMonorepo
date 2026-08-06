# Current Task

## Add Interactive Try-It Controls To Model Instance API Docs

Status: executed.

### Goal

Extend the `/modelInstanceApi` documentation page with interactive request
execution controls so a developer can try documented root-scoped instance-data
API operations directly from the UX.

### Scope

- Add `try it` controls to the existing `/modelInstanceApi` route.
- Reuse the selected `modelAzName` and `instanceRootId` from the API docs route
  query string.
- Generate editable request inputs from the documented operation metadata and
  selected model metadata.
- Let users execute root-scoped query and modification operations against the
  selected model-instance root.
- Show request method, resolved URL, request body, status code, response body,
  and any error message.
- Present modifying operations as available development-mode operations.
- Keep operations limited to root-scoped instance-data routes.
- Preserve read-only documentation usability for users who do not execute
  requests.

### Completion Notes

- Added interactive `Try it` panels to entity and association operations on
  `/modelInstanceApi`.
- Loaded and retained the configured backend base URL so each operation can be
  executed against the same root-scoped paths shown in the documentation.
- Generated editable JSON request bodies from metadata-derived examples for
  create, update, query, and association-link creation operations.
- Added an `instanceId` input for read and update operations whose documented
  path contains `{instanceId}`.
- Displayed the executed method, resolved URL, status code, response body, and
  error message inline without replacing the documentation examples.
- Updated `docs/architecture_doc.md` to reflect the concrete interactive API
  docs behavior.
- Marked the corresponding backlog item executed while keeping it in
  `tasks/backlog.md` as history.
