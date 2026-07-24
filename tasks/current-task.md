# Current Task

## Align Terminal And Browser Console CLI Command Coverage

Status: executed.

### Goal

Make the browser `/console` virtual CLI functionally match the terminal
`VedenemoCli` command surface except for local filesystem commands.

### Scope

- Add shared prompt-state handling so browser console sessions can complete
  multi-step command flows over multiple HTTP command submissions.
- Support browser console flows for:
  - `add`
  - `attr add`
  - `assoc add`
  - `assoc add ownership`
  - `assoc add reference`
  - `assoc add relation`
- Keep `save`, `snapshots`, and `load` terminal-only because they require local
  filesystem access.
- Keep terminal stdin/stdout and local file handling in `vedenemo-cli`.
- Keep browser UI and HTTP session handling in `vedenemo-ux` and
  `vedenemo-web-api`.

### Completion Notes

- Extended `vedenemo-command-console` `ConsoleSession` with prompt-flow state
  for model, entity, attribute, directed association, and relation creation.
- Browser console `help` now lists the same supported non-file authoring
  commands as terminal CLI and clearly marks file commands as unsupported.
- Browser console Esc cancellation now sends a cancellation command to the
  backend so pending prompt state is abandoned server-side.
- Browser console blank Enter is allowed while answering prompts, so defaulted
  prompt values can be accepted.
- Added focused shared-console tests for help, add/entity/attribute/association
  flows, relation kind shortcut, Esc cancellation, and unsupported file
  commands.
- Added a web API console-session prompt-flow test.
- Updated README, CLI reference, implementation architecture documentation, and
  backlog history.
- `mvn -B -pl vedenemo-command-console test` passed.
- `mvn -B verify` passed.
- `npm run build` passed in `vedenemo-ux`.

### Next Steps

- Review browser console command parity through the Firebase UX after
  deployment.
