# ADR-001: Core module uses only JDK dependencies

Status: Accepted

## Context

Vedenemo core is intended to remain portable, stable, long-lived, and minimally coupled to third-party libraries.

## Decision

The core module may depend only on:

- Java JDK
- Vedenemo-owned modules

Third-party dependencies must exist only in adapter modules.

## Consequences

Benefits:

- stable core
- easier long-term maintenance
- simpler AI reasoning
- reduced framework coupling

Tradeoffs:

- more adapter code
- less convenience
- explicit wiring required
