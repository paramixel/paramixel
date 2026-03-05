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

## What to do

1) Read the spec sources listed above and extract concrete, testable requirements.

2) Inspect the codebase for violations, including:
- naming / style / conventions violations
- cross-module dependency violations
- API contract mismatches (route/method, request/response shape, auth)
- data model inconsistencies (missing fields, wrong types, invariant violations)
- missing or nonconforming tests (per testing rules)

3) Produce a report with:
- violations (file path + symbol + line number if available)
- severity (P0/P1/P2)
- recommended minimal fix

## Output

- If violations are found: create an ISSUES.md. The file should have the list of them with file path and line number where applicable.
- If a violation in ISSUES.md is resolved, remove it from ISSUES.md
- If no violations are found: output `Specification conformance check passed.`

