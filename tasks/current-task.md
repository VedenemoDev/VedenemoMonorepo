# Current Task

## Refactor CLI For Shared Terminal And Web Console Use

Status: executed.

### Goal

Refactor CLI-like command handling so it can be used from both:

- the existing terminal `VedenemoCli`;
- a virtual CLI exposed in the UX at `/console`.

The web console must not spawn the Java CLI process or duplicate command
behavior in TypeScript. Shared command parsing, session-oriented command
execution, and user-facing output belong in Vedenemo-owned Java code.

### Scope

- Add a shared Java module for CLI-like command behavior.
- Keep terminal stdin/stdout and local file access in `vedenemo-cli`.
- Keep browser console session identity and UI rendering in `vedenemo-web-api`
  and `vedenemo-ux`.
- Add backend console-session endpoints:

```text
POST /console/sessions
POST /console/sessions/{sessionId}/commands
DELETE /console/sessions/{sessionId}
```

- Use a browser-facing console-session wrapper id that owns an internal backend
  edit session id.
- Add a separate full-page UX route at `/console`.
- Auto-bind the web console to the currently connected model only when the main
  UX has an active model connection.
- Return plain text unsupported messages for `save` and `load` in the web
  console.

### Completion Notes

- Added `vedenemo-command-console` as the shared command-flow module.
- Refactored terminal CLI client DTO/interfaces into the shared module.
- Added web API console-session registry, in-process console adapters, and
  `/console/sessions` endpoints.
- Added `/console` in the Vite UX with terminal-like history, command input,
  session startup, command execution, and best-effort session cleanup.
- The main UX now links to `/console` and passes the active connected model
  `azName` when connected.
- Web console `save` and `load` return clear unsupported local file access
  messages.
- Updated implementation architecture and README documentation.
- Added focused shared-console and web API tests.
- `npm run build` in `vedenemo-ux` passed.
- `mvn -B clean verify` passed.
