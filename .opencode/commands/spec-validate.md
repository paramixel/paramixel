---
description: Validate codebase against Spec-Kit system spec and report gaps
---

# Spec Validation (Spec ↔ Code Conformance + Gap Report)

You are auditing the repository's implementation against the system specification under `.specify/specs/system/`.

## Sources of truth (read these first)
- `.specify/constitution.md` (global rules)
- `.specify/specs/system/00-overview.md`
- `.specify/specs/system/01-architecture.md`
- `.specify/specs/system/02-data-model.md`
- `.specify/specs/system/03-api-contracts.md`
- `.specify/specs/system/04-modules.md`
- `.specify/specs/system/05-conventions.md`
- `.specify/specs/system/06-testing.md`
- `.specify/specs/system/agent-rules.md` (if relevant)

## Goal
Produce a **repeatable** report that answers:
1) Does the code conform to the spec?
2) Where are the gaps (missing implementations)?
3) Where is there drift (code contradicts the spec)?
4) Where are tests missing?

## Method
### Step 1 — Extract requirements
Read the spec files and extract a requirement list. Include:
- MUST / REQUIRED rules
- SHOULD rules (track separately as “should”)
- API endpoints (routes, methods, request/response schemas, auth requirements)
- Data model fields/invariants
- Module boundaries / layering rules
- Conventions (naming, error handling, logging, config patterns)
- Testing rules (required test types/locations, coverage expectations)

Represent each requirement with an ID:
- Format: `SYS-<section>-<nnn>` (e.g. `SYS-API-010`, `SYS-DATA-003`)

### Step 2 — Find implementation evidence
For each requirement:
- Find concrete evidence in code: file path + stable locator (symbol, class, function, route registration).
- Include line numbers if available; if not, include the best stable locator (symbol name + file path).
- If you cannot find evidence, mark as GAP.

### Step 3 — Classify results
Use exactly these statuses:
- **PASS**: requirement is implemented and aligned with spec
- **PARTIAL**: some evidence exists, but missing behaviors/edge cases/constraints
- **GAP**: no evidence of implementation
- **DRIFT**: implementation contradicts spec (wrong behavior/shape/contracts)
- **AMBIGUOUS**: spec is unclear; do not guess—recommend a clarification

### Step 4 — Test coverage mapping
For each requirement:
- Identify tests that verify it (unit/integration/e2e).
- If missing tests, note “missing” and recommend test(s) to add.

### Step 5 — Produce prioritized fix plan
Prioritize fixes as:
- **P0**: security/data loss/corruption, critical API breakage
- **P1**: correctness, major feature gaps, major contract mismatches
- **P2**: maintainability, conventions, minor gaps, refactors

## Output format (strict)
### A) Summary
- Overall status: PASS / FAIL
- Counts: PASS / PARTIAL / GAP / DRIFT / AMBIGUOUS
- Top 5 critical issues (bullets)

### B) Section-by-section conformance
For each section (00–06), output a table with columns:
- Requirement ID
- Requirement (short)
- Status
- Evidence (file path + symbol/locator)
- Notes / edge cases
- Tests (file path or “missing”)

### C) Drift details
For each DRIFT:
- Spec statement (paraphrase)
- Actual behavior in code
- Location (file + symbol/locator)
- Recommendation: change spec OR change code (pick one and justify)

### D) Remediation plan
Numbered list; each item includes:
- Priority (P0/P1/P2)
- What to change
- Exact files to touch
- Tests to add/update

## Rules
- Do **not** invent requirements not in the specs.
- If the codebase has multiple implementations, choose the one in active use and explain how you determined that (entrypoints, routing, DI wiring, etc.).
- If you need me to run any commands (tests/build/lint), ask clearly and specify the command(s) you want run and what output you need.

