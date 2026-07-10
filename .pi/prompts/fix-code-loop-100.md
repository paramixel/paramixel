# Fix Code Loop × 100 — All Modules

Run up to 100 complete bounded loops of analysis → design → specification → implementation.
Each phase must complete before the next phase begins.

## Execution Boundary

This is an orchestration prompt. It may create planning artifacts and modify
code/tests only during the implementation phase of a confirmed, bounded issue.

- Do not make unrelated changes.
- Do not fix speculative findings.
- Do not duplicate fixes across loops.
- Do not commit, push, release, install dependencies, use network services, open
  browsers, or use editor-specific actions unless explicitly requested by the
  user or required by repository guidance.
- Use project-discovered build, test, format, and validation commands.

## Objective

Find and fix correctness issues in the target repository using up to 100 loops of
this deterministic phase sequence:

1. `analyze-code.md` — evidence-backed correctness analysis.
2. `create-design-plan.md` — design plan for confirmed issues.
3. `create-implementation-spec.md` — reproduction-first implementation spec.
4. `implement-spec.md` — scoped implementation and validation.

## Input

$ARGUMENTS

The target repository, module, package or class, issue reference, or scope
constraint. If omitted, inspect repository guidance to identify a default target
scope. If no target scope can be identified, ask for the target or report a
blocker.

## Required Preflight

Before loop 1:

- Read repository guidance, build/configuration files, and prompt-relevant
  source/test structure.
- Identify modules or packages or classes in scope.
- Identify validation commands from repository guidance.
- Record any constraints that affect public API, exception handling,
  compatibility, concurrency, or lifecycle.

## Loop State

Maintain this table and update it after each phase:

| Loop | Selected issue | Analysis result | Design plan path | Spec path | Files changed | Validation status | Stop reason |
| --- | --- | --- | --- | --- | --- | --- | --- |

Use `Not applicable` for fields that do not apply.

## Issue Selection Rules

For each loop:

- Select the smallest high-confidence correctness issue with repository
  evidence.
- Prefer issues with observable incorrect behavior and a clear reproduction or
  missing-behavior test.
- Exclude style-only, broad redesign, low-confidence, and product-decision
  issues.
- If multiple issues are found, fix one cohesive issue or tightly related set
  per loop.
- Do not select an issue already fixed in a prior loop.

## Phase 1: Analyze Code

Use the language-specific `analyze-code.md` rules.

### Deliverables

- Confirmed findings grouped by severity.
- Exact files and methods, classes, and interfaces involved.
- Evidence, trigger scenario, impact, suggested fix, and suggested test.
- `Needs confirmation` for suspicious but unconfirmed observations.

### Decision Gate

Proceed only if there is at least one confirmed, bounded, reproducible issue.
If findings are speculative or require unresolved product decisions, stop or
move to the next candidate within the loop limit.

## Phase 2: Design Plan

Use `create-design-plan.md` for the selected issue.

### Deliverables

- Plan path under `.pi/plans/` unless the user supplied another path.
- Problem statement, goals/non-goals, existing code, proposed design,
  compatibility, exception handling, concurrency/lifecycle, test strategy, and
  acceptance criteria.
- No code or test changes in this phase.

## Phase 3: Implementation Specification

Use `create-implementation-spec.md` for the design plan.

### Deliverables

- Spec path under `.pi/specs/` unless the user supplied another path.
- Reproduction-first or missing-behavior-first test instructions.
- Ordered implementation steps.
- Exact files, signatures, behavior rules, exception handling, validation, and
  acceptance criteria.
- No code or test changes in this phase.

## Phase 4: Implement Specification

Use `implement-spec.md` for the generated specification.

### Deliverables

- Tests added or updated as specified.
- Minimal scoped source changes.
- Repository-discovered formatting and validation run with results.
- Final implementation report with changed files, validation status, blockers,
  and assumptions.

## Optional Commit Handling

Commit or push only when explicitly requested by the user or documented by the
target repository for this task. If requested, use the repository's commit
message and sign-off conventions.

## Stop Conditions

Stop and report the reason if:

- No confirmed bounded issue exists.
- Required repository context cannot be read.
- A selected issue cannot be reproduced or specified.
- A design decision remains unresolved.
- Implementation would require unrelated changes.
- Validation reveals unrelated failures that cannot be safely handled in scope.
- Stop after 100 successful loops, or earlier if no confirmed bounded issues remain.

## Final Report

Report:

- completed loop count;
- loop state table;
- files changed;
- design/spec artifact paths;
- validation commands and results;
- blockers, assumptions, and remaining confirmed issues if any.
