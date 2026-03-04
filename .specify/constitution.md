# Paramixel Constitution

This document captures stable project principles extracted from the existing Paramixel system specification and agent rules.
It does not introduce new requirements.

## Principles

1. Specification-first
   - The authoritative system specification is maintained as documents under `.specify/specs/system/`.
   - Any change that affects the API model must be reflected in the data model documentation.
   - Any change that adds or changes an API must be reflected in the API contracts documentation.

2. Strong module boundaries
   - `paramixel-api` contains only public annotations and context interfaces.
   - `paramixel-engine` contains the JUnit Platform engine implementation.
   - `paramixel-maven-plugin` contains Maven Mojo integration only.
   - Functional tests live in `tests/` and examples live in `examples/`.

3. Conventions are enforced, not optional
   - Every Java file starts with the Apache 2.0 license header from `assets/license-header.txt`.
   - Formatting is applied by Spotless (Palantir Java Format) during `./mvnw compile`.
   - Constructor injection only; no field injection.
   - No new Maven dependencies without explicit approval.

4. Deterministic lifecycle semantics
   - Lifecycle hooks and ordering behave exactly as described in the system spec.
   - "After" hooks must run even after failures, consistent with the spec.

5. Safe concurrency
   - Parallel execution uses Java virtual threads.
   - Concurrency is limited by fair semaphores as defined in the engine design.

## TODO

- Confirm whether to keep `AGENTS.md` at repo root for tooling compatibility or fully relocate it into `.specify/specs/system/`.
