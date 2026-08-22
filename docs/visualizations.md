# Visualizations

Vedenemo currently includes a runtime-only visualization proof of concept for
model instance data in the browser UX. It lets a user map model elements to a
chart-specific binding and render the selected model-instance root without
persisting the chart configuration.

The implemented chart types are D3-backed `Tidy tree`, `Radial tree`, and
`Tree of life`.

## Scope

Visualizations are frontend-only runtime views over process-local backend
instance data.

They do not change:

- model definitions
- model-instance data
- `.vdos` scripts
- backend storage
- core command behavior

Visualization bindings are not saved. Reloading the page or opening a new
wizard tab starts with a fresh runtime binding.

## Prerequisites

Before opening a visualization:

1. Start `vedenemo-web-api`.
2. Start the Vite UX.
3. Load or create a model.
4. Create or load at least one model-instance root.
5. Add instance data and association links that match the chart you want to
   render.

The current proof path uses the `AlbumCollectionSimple` model loaded from
`.vedenemo/LevykokoelmaSimple.vdos`, with sample data loaded by
`scripts/LoadLevykokoelmaSimpleModelData.bash`.

The repository also includes `FamilyTreeTidy`, a Tidy tree-oriented family
projection model in `.vedenemo/FamilyTreeTidy.vdos`. Its sample loaders are:

- `scripts/LoadBritishRoyalFamilyTreeTidyModelData.bash`
- `scripts/LoadSwedishRoyalFamilyTreeTidyModelData.bash`

## Opening The Wizard

In the UX main page:

1. Open the `Model instances` tab.
2. Refresh model instances if needed.
3. Open a model-instance root node menu.
4. Choose `Visualize...`.

The UX opens a new browser tab at:

```text
/visualizeWizard?modelAzName={modelAzName}&instanceRootId={instanceRootId}
```

The wizard reads runtime config from `/config.json`, then loads root-scoped API
metadata and root metadata from the backend.

## Wizard Steps

The wizard has three steps.

### Chart Type

The chart type step lists available chart types. `Tidy tree`, `Radial tree`,
and `Tree of life` are currently implemented.

The wizard disables invalid chart types and shows the reason. For all tree
charts, the selected model must have at least one association between entities.

### Binding

The binding step maps model elements to chart concepts.

For `Tidy tree`, `Radial tree`, and `Tree of life`, the binding includes:

- chart root label
- one or more entity levels
- association traversal between levels
- traversal direction, either `outgoing` or `incoming`
- label templates for each entity level

Label templates use attribute placeholders:

```text
{Name}
{Name} ({year})
{id}
```

Placeholders must refer to modeled attributes on the selected entity, except
for `{id}`, which uses the backend-assigned instance id.

The binding form shows template hint buttons for the selected entity. Click a
hint, such as `{Name}` or `{id}`, to insert it into the label template at the
last cursor position in the input. The inserted text can then be combined with
typed punctuation or other hint values.

The binding prevents cyclic entity paths. An entity can appear only once in the
selected tree path.

### Visualization

The visualization step fetches real root-scoped instance data:

- entity instances through
  `POST /data/{modelAzName}/roots/{instanceRootId}/{entityAzName}/_query`
- association links through
  `GET /data/{modelAzName}/roots/{instanceRootId}/_links/{associationAzName}`

The UX transforms the selected entities and links into chart tree data and
renders a scrollable SVG tree with D3. `Tidy tree` uses the rectangular D3 tree
layout. `Radial tree` uses the same hierarchy and binding data with D3 radial
links and radial node placement. `Tree of life` uses the same hierarchy and
binding data with a D3 radial cluster layout, leaf labels on a common outer
rim, and faint label-extension links. Branch length is not modeled or used.

Use `Refresh` to reload backend data without losing the current runtime binding.

## Tidy Tree Example

For the `AlbumCollectionSimple` proof case:

1. Use chart root label `Mikan levykokoelma`.
2. Select `Artist` as the first entity level.
3. Add an `Album` level through `Artistilla_on_albumeja`.
4. Use outgoing traversal from `Artist` to `Album`.
5. Use label templates such as `{Name}` for artists and `{Name} ({year})` for
   albums.

The resulting tree renders the model-instance root as the top node, artist
nodes under it, and album nodes under each artist.

For the `FamilyTreeTidy` proof cases, the model keeps canonical `Person` and
`FamilyUnit` instances and adds depth-specific display-node entities for the
tree projection. This allows one person to appear in multiple rendered places,
such as spouse and child references, while each display node still references
one canonical `Person`.

Use one of the loaded roots:

- `British Royal Family Tree Tidy`
- `Swedish Royal Family Tree Tidy`

Then bind the tree chart through the outgoing display-node path:

```text
FamilyUnitNode0
PersonNode1
FamilyUnitNode2
PersonNode3
FamilyUnitNode4
PersonNode5
FamilyUnitNode6
PersonNode7
```

Useful label templates are `{Label}` for family-unit nodes and `{Label}
[{Role}]` for person nodes. The depth-specific node entities avoid repeated
entity types in the binding path, which keeps the current cyclic-path guard
intact while still rendering a one-direction family tree projection.

## Current Limits

- `Tidy tree`, `Radial tree`, and `Tree of life` are the implemented chart
  types.
- Visualization bindings are runtime-only and are not stored.
- The flow is intended as a proof of concept, not a general-purpose chart
  designer.
- The visualization reads process-local backend data. It does not add durable
  persistence.
- The chart currently uses entity paths and association links; it does not
  infer hierarchy automatically without a user-selected binding.
- `Tree of life` currently uses constant-depth cluster layout only; branch
  length is intentionally skipped.

## Related Docs

- [README](../README.md)
- [Architecture document](architecture_doc.md)
