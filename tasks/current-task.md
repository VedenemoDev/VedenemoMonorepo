# Current Task

## Hide Hexbin-map part-to-whole ownership association choices

Status: executed

### Goal

Prevent the `Hexbin-map` visualization setup flow from offering directed
whole-part ownership associations in the part-to-whole direction.

For a model such as `Metsapalsta.vdos`, where `Metsapalsta` owns
`Metsakuvio`, the user should be offered the viable whole-to-part traversal
from `Metsapalsta` to owned `Metsakuvio` instances. The inverse traversal from
`Metsakuvio` back to owning `Metsapalsta` should not appear as a selectable
Hexbin-map association path because it does not produce useful subregion map
results.

### Scope

- Update the browser UX Hexbin-map association-selection behavior.
- Use existing association kind and source/target metadata.
- Keep the model, `.vdos`, HTTP API, and CLI association semantics unchanged.
- Keep non-Hexbin-map traversal behavior unchanged.

### Out Of Scope

- Changing ownership association semantics in core model code.
- Removing valid part-to-whole traversal from non-map query/runtime behavior.
- Redesigning ordered association traversal generally.
- Adding new backend endpoints solely for this filter unless existing API data
  proves insufficient.
- Changing `Metsapalsta.vdos` model content.

### Acceptance Criteria

- In `Metsapalsta.vdos`-based data, Hexbin-map offers the
  `Metsapalsta` to `Metsakuvio` ownership traversal for subregion overlays.
- The inverse `Metsakuvio` to `Metsapalsta` ownership traversal is not offered
  as a Hexbin-map selectable association path.
- Existing useful Hexbin-map association choices for non-ownership or
  whole-to-part ownership flows remain available.
- Runtime/model data can still represent and traverse ownership links in both
  directions where other features need that behavior.
- Frontend build succeeds.

### Completion Notes

- Kept generic traversal option generation unchanged for query, tree, runtime,
  and other non-map flows.
- Added a Hexbin-map-specific overlay traversal filter in `vedenemo-ux` that
  hides `OWNERSHIP` associations when they would be traversed in the incoming
  part-to-whole direction.
- Preserved outgoing whole-to-part ownership traversal choices for Hexbin-map
  subregion overlays.
- Reused existing API association kind and source/target direction metadata;
  no backend endpoint, `.vdos`, CLI, or model semantic changes were needed.
- Frontend build and full backend Maven verification succeeded.
