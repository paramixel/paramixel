# AGENTS.md

Guidelines for human and automated agents contributing to this repository.

This file defines REQUIRED behavior for code changes, validation, and safety
constraints when modifying the project.

---

## Project Overview

* **Language:** Java 17
* **Build System:** Maven Wrapper (`./mvnw`)
* **Architecture:** Multi-module Maven project
* **Validation Command (MANDATORY):**

```bash
./mvnw clean verify
```

All changes MUST successfully pass this command.

---

## Core Rules

### 1. Validation Requirement (Non-Negotiable)

Agents MUST validate all changes using:

```bash
./mvnw clean verify
```

A change is invalid if this command fails.

Agents must assume CI executes this exact command.

Do NOT substitute with:

* `mvn`
* `package`
* `install`
* partial module builds

Only `clean verify` is authoritative.

---

### 2. API Module Stability (Critical Rule)

The `api` module defines stable public contracts.

Agents MUST NOT:

* modify public interfaces
* change method signatures
* alter DTO schemas
* rename exported classes
* change serialization formats
* introduce breaking behavioral changes
* create unit tests for interface classes
* create unit tests for annotations

Allowed changes inside `api`:

* comments
* documentation improvements
* internal non-public refactoring
* test additions (if present)

If a requested task requires API changes:
➡️ STOP and explain why the change violates this rule.

---

## Agent Operating Principles

1. Prefer **minimal, targeted changes**.
2. Preserve existing architecture and patterns.
3. Do not introduce large refactors unless explicitly requested.
4. Maintain production-grade reliability.
5. Assume backward compatibility matters.

Agents must prioritize correctness over stylistic improvement.

---

## Build & Test Workflow

### Standard workflow

```bash
./mvnw clean verify
```

This may include:

* compilation
* unit tests
* integration tests
* static analysis plugins
* packaging validation

Agents should assume all lifecycle checks are meaningful and required.

---

## Code Change Guidelines

### Java Standards

* Target Java 17 compatibility.
* Prefer readability over cleverness.
* Follow existing package and naming conventions.
* Avoid introducing new frameworks unless necessary.
* Implement comprehensive unit tests.

### Error Handling

* Never swallow exceptions silently.
* Preserve existing logging behavior.
* Add context without exposing sensitive data.

### Logging

* Use existing logging framework only.
* Never log:

  * secrets
  * credentials
  * tokens
  * private keys

---

## Dependency Rules

When adding dependencies:

* Prefer small, well-maintained libraries.
* Avoid overlapping functionality with existing dependencies.
* Keep dependency scope minimal.
* Do not upgrade unrelated dependencies.

---

## Testing Requirements

### Requirement: tests must cover all changes

All code changes MUST be accompanied by unit tests that validate the new or modified behavior.

* Bug fixes MUST include a regression test.
* Refactors that alter control flow or logic MUST be covered by tests.
* Purely non-functional changes (comments, formatting) are the only exception.

### Testing stack

* Use **JUnit 6** for unit tests.
* Use **AssertJ** for assertions.
* **Mockito is allowed but should be minimized**.

  * Prefer real objects, fakes, or small in-memory implementations over deep mocking.
  * If Mockito is used, keep mocks focused and avoid over-specifying interactions.

### Determinism

Tests must be deterministic and compatible with `./mvnw clean verify`.

Avoid:

* timing-sensitive tests
* external network calls
* environment-dependent behavior

---

## Repository Assumptions

This repository intentionally has:

* ❌ No README.md files
* ❌ No documentation requirements tied to README updates

Agents MUST NOT create README files unless explicitly requested.

---

## Security Requirements

Agents MUST:

* never introduce credentials
* avoid insecure defaults
* validate inputs when adding new logic
* avoid exposing internal system details in errors

If a potential vulnerability is discovered, clearly note it in the change summary.

---

## Pull Request Expectations

Each change should include:

* **What changed**
* **Why**
* **How validated** (`./mvnw clean verify`)
* **Risk assessment**

Example:

```
Summary:
- Fix null handling in OrderService

Validation:
- Ran ./mvnw clean verify successfully

Risk:
- Low, internal logic only
```

---

## Forbidden Actions

Agents MUST NOT:

* modify the `api` module public surface
* fabricate build results
* claim tests passed without validation
* introduce unrelated refactors
* change project structure without instruction
* run `git commit`
* run `git reset` (any mode)
* rewrite repository history
* perform destructive git operations (`rebase`, `push --force`, `clean`, etc.)

Agents may prepare changes but version control actions must be performed by a human unless explicitly authorized.

---

## Definition of Done

A change is complete when:

✅ `./mvnw clean verify` succeeds
✅ API module contract unchanged
✅ Unit tests cover all behavior changes (JUnit 6 + AssertJ)
✅ Mockito usage is minimal and justified
✅ No secrets introduced
✅ Changes remain minimal and focused

---

## Decision Priority Order

When rules conflict, follow this order:

1. API stability rule
2. Successful `./mvnw clean verify`
3. Backward compatibility
4. Minimal change principle
5. Style consistency

---

