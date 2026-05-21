# ADR-002: Use Java 21 and Maven for canonical backend

Status: Accepted

## Context

The canonical backend should be easy to review, easy to compile in CI, and suitable for long-term maintenance.

## Decision

Use Java 21 and Maven for the canonical backend modules.

## Consequences

Benefits:

- predictable CI
- simple module structure
- strong compatibility with GitHub Actions
- easy review for developers experienced with Java and Maven

Tradeoffs:

- less concise than Kotlin
- less experimental language exploration in the core implementation
