# Fix Code Loop

Run one complete loop of analysis → design → specification → implementation
on a target module. Execute each phase in sequence, resolve any issues
found, and produce only the final code changes and supporting documents.

$ARGUMENTS

## Execution Boundary

This prompt orchestrates a four-phase loop:

1. `analyze-code.md` — deep correctness analysis of the target module.
2. `create-design-plan.md` — design plan to resolve confirmed issues.
3. `create-implementation-spec.md` — implementation specification from the
   design plan.
4. `implement-spec.md` — implement the specification.

Each phase must complete fully before the next phase begins. The handoff
from each phase to the next is the document produced: findings → design
plan → implementation spec → code changes.

## Objective

Identify correctness issues in the target module through deep analysis,
produce a design plan to resolve them, convert that plan into a concrete
implementation specification, and implement the specification — all in one
continuous loop.

## Input

Path to the target module. Defaults to the project's core module if the
project has one. If no default module exists and no path is specified, ask
for the target module before proceeding.

Optionally, a scope constraint: specific packages, classes, or issue
references to narrow the analysis.

## Phase 1: Analyze Code

Execute `analyze-code.md` for the target module.

### Deliverables

- Complete findings report with severity levels: Critical, High, Medium, Low.
- Confirmed bugs tied to specific files, methods, and observable incorrect
  behavior.
- A "needs confirmation" section for suspicious findings lacking sufficient
  evidence.

### Decision Gate

After producing the findings report, determine which issues to resolve in
this loop:

- **If Critical or High issues exist**: resolve all Critical and High
  issues. Resolve Medium and Low issues at your discretion based on
  severity, fix complexity, and risk of regression.
- **If no Critical or High issues exist**: resolve the most impactful
  Medium and Low issues. Do not skip the remaining phases — there is always
  something to improve.
- **If no issues are found at all**: produce a brief summary stating that
  analysis found no correctness issues and stop. Do not fabricate problems.

Report the number of issues selected for resolution before moving to Phase
2.

## Phase 2: Design Plan

Execute `create-design-plan.md` with the selected findings from Phase 1 as
the problem statement.

### Deliverables

- Design plan document written to `.pi/plans/fix-<description>.md` covering
  all selected issues.
- Concrete approach, tradeoffs, test strategy, and acceptance criteria for
  each fix.

## Phase 3: Implementation Specification

Execute `create-implementation-spec.md` with the design plan from Phase 2
as input.

### Deliverables

- Implementation specification document written to
  `.pi/plans/fix-<description>-spec.md` (or append `-spec` to the design
  plan path).
- Ordered implementation steps beginning with reproduction tests for every
  confirmed bug.
- Exact files, method signatures, behavior rules, and acceptance criteria.

## Phase 4: Implement Specification

Execute `implement-spec.md` with the implementation specification from
Phase 3 as input.

### Deliverables

- Reproduction tests that fail before the fix and pass after.
- The smallest focused source changes that resolve every selected issue.
- Passing test suite with no regressions.
- All acceptance criteria from the specification satisfied.

## Phase Handoffs

- The deliverable from each phase is the input to the next.
- Wait for each phase to complete before starting the next.
- Start Phase 1 by reading the target module files.

## Stop Conditions

Stop the entire loop and report the blocker if:

- **Phase 1**: The target module cannot be located or its files cannot be
  read, or the code cannot be understood sufficiently to identify contracts.
- **Phase 2**: The selected issues are too vague to produce a concrete
  design, or required design decisions remain unresolved.
- **Phase 3**: The design plan is too incomplete to convert into a
  specification, or the expected failing behavior cannot be identified.
- **Phase 4**: The specification is ambiguous, contradicts project
  conventions, or describes unreproducible issues.

## Completion Criteria

The loop is complete when:

- Phase 1 produced a findings report covering all source files in the
  target module.
- Phase 2 produced a design plan document for the selected issues.
- Phase 3 produced an implementation specification with reproduction tests
  and ordered steps.
- Phase 4 produced passing reproduction tests, the minimal source changes
  to resolve every selected issue, and a clean full test suite with no
  regressions.
- Every document and code change follows the project's agent instructions
  for formatting, validation, and commit conventions.

In the final response, report:

- The number of issues found and resolved.
- A summary of each fix applied.
- The paths of all documents produced (findings report, design plan,
  implementation specification).
- The changed source and test files.
- The validation commands run and their results.
