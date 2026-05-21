# Vedenemo Initial Structure

This repository is an initial compiling skeleton for Project Vedenemo.

## Current scope

- Java 21 + Maven backend modules
- Pure JDK-only core rule
- SPI abstraction layer
- One initial adapter: in-memory model storage
- Minimal CLI and application composition root
- Separate Vite/TypeScript UX skeleton
- GitHub Actions workflows for backend and frontend

## Backend build

```bash
mvn clean verify
```

## Frontend build

```bash
cd vedenemo-ux
npm ci
npm run build
```

## Current milestone

The project should compile and provide a safe foundation for later agentic development.
It intentionally does not implement real Vedenemo modeling behavior yet.
