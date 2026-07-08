# Vedenemo Agent Instructions

## Mission

Build Vedenemo incrementally while preserving strict architectural boundaries.

The first goal is a compiling multi-module project skeleton.

## Priority order

When instructions conflict, follow this priority:

1. Non-negotiable architectural rules
2. Current task definition
3. Existing code style and module structure
4. General improvement suggestions

## Read first

Before making changes, read:

- `docs/architecture/dependency-boundaries.md`
- `docs/architecture/module-map.md`
- `docs/architecture/coding-rules.md`
- `docs/architecture/testing-strategy.md`
- `docs/roadmap/first-milestone.md`
- `tasks/current-task.md`

## Non-negotiable rules

### Core purity

`vedenemo-core` must not directly depend on third-party libraries.

Allowed:

- Java JDK APIs
- Vedenemo-owned modules

Forbidden:

- Spring
- Jackson
- Gson
- SLF4J / Logback
- Hibernate
- ANTLR
- Guice
- Weld
- external dependency injection frameworks
- database drivers
- HTTP frameworks

### Dependency direction

Core must never depend on:

- adapters
- CLI
- UX
- infrastructure libraries

### Composition

Use explicit constructor wiring.

Do not introduce a dependency injection framework.

Application assembly belongs in `vedenemo-app` or `vedenemo-cli`.

## Preferred implementation style

Prefer:

- small explicit classes
- immutable records where appropriate
- deterministic behavior
- straightforward constructor injection
- readable code over clever abstractions

Avoid:

- reflection-heavy solutions
- framework magic
- premature generic abstractions
- hidden runtime behavior

## Current implementation phase

See:

- `tasks/current-task.md`

Current focus:

- compiling Maven multi-module structure
- minimal SPI abstraction
- in-memory storage adapter
- separate Vite frontend

Do not yet implement:

- REST APIs
- WebSockets
- databases
- parser generators
- persistence formats
- distributed runtime

## Build commands

Backend:

```bash
mvn clean verify
```

Frontend:

```bash
cd vedenemo-ux
npm ci
npm run build
```

## Modification policy

Do not modify architectural documents unless explicitly instructed.

Architectural documents include:

- `docs/architecture/*`
- `decisions/*`

## Persistent session record

Each agent session must be recorded in `SESSION.md`.

At the start of a session, read the latest entries in `SESSION.md` after reading
the required project documents.

Before finishing a session, append a concise entry that includes:

- date and time
- session goal
- files changed
- commands run
- current status and next steps

## Architecture documentation

The repository separates architecture definition/planning documents from the
current implementation architecture document.

Architecture definition and planning documents describe rules, constraints,
decisions, intended direction, and future prospects. These include:

- `docs/architecture/*`
- `docs/roadmap/*`
- `decisions/*`

`docs/architecture_doc.md` is a special case. It is living documentation of the
current concrete implementation only. It must not describe planned architecture
as if it already exists.

When making any meaningful architectural change, update `docs/architecture_doc.md` in the same change.

Before updating it, read `docs/architecture_doc_instructions.md` and follow its rules.

Use Mermaid diagrams directly in Markdown. Do not generate PNG/SVG diagram artifacts unless explicitly requested.

Architectural changes include, for example:

- new modules or packages
- changed component boundaries
- new dependencies between components
- new runtime flows
- new extension points
- changed persistence, API, CLI, plugin, or visualization structure
- removed or renamed architectural concepts
