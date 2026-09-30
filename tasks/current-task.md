# Current Task

## Value set editing reboot

Status: executed

### Goal

Reboot value-set editing around the correct model-level concept and implement
the first narrow authoring slice: adding new visible values to existing
model-level value sets from the selected model context.

### Scope

- Add a model-level `Model tools` entry point for value-set editing in
  `vedenemo-ux`.
- Implement an add-only value-set editor for existing model-level value sets.
- Prioritize visible-name input and derive hidden/read-only technical values.
- Show selected value-set usage by bound model attributes.
- Preserve strict module boundaries and use the existing backend value-set
  replacement capability.

### Completion Notes

- Added a `Model tools` panel to the `Models` view with a `Value sets` tool.
- Kept the model diagram visible beside the tool on wide layouts.
- Made the value-set tool the primary content on narrow layouts while it is
  open, with a `Back to overview` return path.
- Removed the visible `Value sets` tab entry point from the model-instance
  `/editor` flow.
- Implemented add-only editing for existing `TEXT` value sets.
- Displayed existing values as read-only rows and listed model attributes bound
  to the selected value set.
- Used a visible-name-first add flow and derived an `azName`-style ASCII
  technical value automatically.
- Rejected duplicate derived technical values in the UI before save.
- Reused the existing backend value-set replacement endpoint through ephemeral
  backend sessions; backend, core, CLI, `.vdos`, and `.vdmp` behavior were
  unchanged.
- Verified with `npm run build` in `vedenemo-ux`.
