# Current Task

## Higher-confidence current-location capture

Status: executed

### Goal

Improve the Entity data editor's `Use current location` behavior so captured
`LOCATION` values come from a bounded repeated-read browser location session
instead of a single cached-or-immediate reading.

### Scope

- Keep the implementation in `vedenemo-ux`.
- Replace one-shot `getCurrentPosition` capture with a short `watchPosition`
  capture session for single-point `LOCATION` fields.
- Track latest reading, best reading, reported accuracy, elapsed time, and
  reading count.
- Let the user accept the best reading or cancel the capture.
- Preserve manual JSON editing and the existing `{latitude, longitude}` saved
  value shape.
- Leave backend, core, CLI, `.vdos`, and `.vdmp` behavior unchanged.

### Completion Notes

- Added structured current-location capture state for Entity data editor
  `LOCATION` fields.
- `Use current location` now starts a bounded high-accuracy `watchPosition`
  session with `maximumAge: 0`.
- The editor shows latest accuracy, best accuracy, elapsed time, reading count,
  guidance text, and `Accept best` / `Cancel` actions.
- Accepting writes the best reading as the existing compact
  `{latitude, longitude}` JSON value; cancelling restores the previous field
  value.
- Watchers and timers are cleared on accept, cancel, timeout, manual field
  change, model/entity/root replacement, and component cleanup.
- Manual `LOCATION` JSON editing and validation remain unchanged.
- Verified with `npm run build` in `vedenemo-ux`.
