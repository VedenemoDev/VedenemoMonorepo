# ADR-003: Keep UX separate from backend build

Status: Accepted

## Context

Vedenemo backend should be testable without running a browser UX.

## Decision

Keep the TypeScript/Vite UX in a separate top-level directory and use separate CI workflow for frontend build.

## Consequences

Benefits:

- backend CI remains headless
- UX can evolve independently
- fewer cross-tooling failures in early project setup

Tradeoffs:

- release packaging needs coordination later
