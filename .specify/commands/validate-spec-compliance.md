---
description: Validate the codebase against the system specification
---

# Validate Spec Compliance

Purpose: verify the codebase conforms to the system specification under `.specify/specs/system/`.

## Checks

- Conventions: `.specify/specs/system/09-conventions.md`
- Module boundaries: `.specify/specs/system/11-modules.md`
- API contracts (public API + lifecycle + integrations):
  - `.specify/specs/system/03-domain-model.md`
  - `.specify/specs/system/04-lifecycle.md`
  - `.specify/specs/system/07-maven-plugin.md`
- Data model (API types + invariants): `.specify/specs/system/03-domain-model.md`
- Testing rules: `.specify/specs/system/10-testing.md`

## Gap Detection

Also detect:
- functionality described in the spec but not implemented
- missing API endpoints
- missing data fields
- missing tests

## Output

- If violations are found: list them with file path and line number where applicable.
- If no violations are found: output `Specification conformance check passed.`
