# Current Task

## Add desktop double-click chart type advance

Status: executed

### Goal

Improve desktop browser ergonomics in the visualization wizard by allowing a
mouse double-click on a selectable chart type to select that chart and advance
directly to the binding step.

The existing single-click selection plus explicit `Continue` button remains
available for all users.

### Scope

- Update the browser UX chart type selection step.
- Keep the existing `Continue` button behavior unchanged.
- Limit the shortcut to desktop-style pointer devices that support hover and a
  fine pointer.
- Do not advance for disabled or non-selectable chart types.

### Out Of Scope

- Backend, API, CLI, or `.vdos` changes.
- Touch/mobile double-tap navigation behavior.
- Changing the visualization binding or rendering steps.
- Adding visible shortcut instructions to the UI.

### Acceptance Criteria

- A selectable chart type still becomes selected on single click.
- The `Continue` button still advances to the binding step when the selected
  chart type is available.
- On desktop-style fine-pointer/hover browsers, double-clicking a selectable
  chart type selects it and advances to the binding step.
- Double-clicking a disabled or non-selectable chart type does not advance.
- Touch/mobile behavior is not given a new double-tap shortcut.
- Frontend build succeeds.

### Completion Notes

- Added a shared chart type selection helper for the visualization wizard.
- Added a double-click handler to selectable chart type buttons that selects
  the clicked chart and advances to the binding step only when
  `(hover: hover) and (pointer: fine)` matches.
- Preserved the existing single-click selection and `Continue` button flow.
- No backend, API, CLI, `.vdos`, or rendering logic changes were needed.
