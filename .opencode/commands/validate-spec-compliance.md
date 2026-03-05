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
