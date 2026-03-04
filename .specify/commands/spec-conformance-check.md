---
description: Verify the codebase conforms to the system specification
---

# Spec Conformance Check

Purpose: verify the codebase conforms to the system specification under `.specify/specs/system/`.

## Checks

- Conventions: `.specify/specs/system/05-conventions.md`
- Module boundaries: `.specify/specs/system/04-modules.md`
- API contracts: `.specify/specs/system/03-api-contracts.md`
- Data model: `.specify/specs/system/02-data-model.md`
- Testing rules: `.specify/specs/system/06-testing.md`

## Gap Detection

Also detect:
- functionality described in the spec but not implemented
- missing API endpoints
- missing data fields
- missing tests

## Output

- If violations are found: list them with file path and line number where applicable.
- If no violations are found: output `Specification conformance check passed.`

