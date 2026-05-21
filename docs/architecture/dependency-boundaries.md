# Dependency Boundaries

## Core rule

`vedenemo-core` must remain free of direct third-party dependencies.

It may depend only on:

- Java 21 JDK APIs
- Vedenemo-owned API/SPI modules explicitly approved as core-facing

The core must not import or depend on implementation libraries such as:

- Jackson
- Gson
- SLF4J / Logback
- ANTLR
- Hibernate
- H2 / PostgreSQL drivers
- HTTP frameworks
- dependency injection frameworks

## Reason

The core model and command execution engine are intended to be long-lived,
portable, testable, and stable across changes in infrastructure libraries.

Third-party libraries are allowed, but only behind Vedenemo-owned abstractions.

## Dependency direction

Core defines what it needs. Adapters implement those needs.

```text
vedenemo-core
    ↓
vedenemo-core-spi

adapter modules
    ↓
vedenemo-core-spi
```

The core must never depend on adapter modules.

## Adapter rule

Adapter modules may depend on third-party libraries when needed.

Adapters must translate between external library types and Vedenemo-owned types.

Third-party types must not leak into core APIs.
