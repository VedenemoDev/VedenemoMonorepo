# Current Task

## CLI roots command for selecting model-instance dump sources

Status: executed.

### Goal

Add a CLI command named `roots` that lists the active model's process-local
model-instance roots so users can discover the root numbers, visible names, and
root ids needed by `dsave`.

### Scope

- Add `roots` to the terminal CLI and browser virtual CLI command surface.
- Require an attached model before listing roots.
- Reuse the existing backend root listing route
  `GET /data/{modelAzName}/roots`.
- Display each root with a stable one-based number for the current listing,
  visible name, model version, and root id.
- Update the latest root-list cache used by `dsave` root-number selection.
- Keep `dumps` focused on `.vdmp` dump artifact listing.

### Acceptance Criteria

- `help` shows `roots` near the data dump commands in terminal and browser
  console modes.
- Running `roots` without an attached model prints a clear message requiring a
  model attachment.
- Running `roots` for an attached model with no instance roots prints a clear
  empty-state message.
- Running `roots` for an attached model with roots lists one-based numbers,
  visible names, model versions, and root ids.
- After `roots`, `dsave <number>` resolves the selected root from the latest
  roots listing.
- Browser virtual CLI `roots` uses backend-managed in-process root listing and
  terminal CLI `roots` uses the HTTP client route.

### Completion Notes

- Added terminal CLI `roots` command.
- Added browser console `roots` command.
- `roots` refreshes the cached root list used by `dsave <number>`.
- Terminal CLI clears cached roots on model attach/detach.
- Updated README, CLI reference, architecture implementation docs, and tests.
- Verified with `mvn -B verify`.
