# Coding Rules

## Java style

- Use Java 21.
- Prefer records for immutable data carriers.
- Prefer sealed interfaces for closed command hierarchies when useful.
- Prefer explicit constructors.
- Avoid reflection unless explicitly justified.

## Dependency style

- Do not add third-party dependencies to `vedenemo-core`.
- Do not leak adapter or third-party types into SPI interfaces.
- Do not introduce a dependency injection framework in the initial phase.

## Error handling

TODO: Define project-wide error handling conventions.

## Naming

TODO: Define final naming conventions for commands, models, and script concepts.
