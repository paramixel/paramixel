# Fix Code Loop × 10

Run ten complete loops of analysis → design → specification →
implementation on a target module. Execute each phase in sequence within
each loop, resolve any issues found, and repeat until ten loops have
completed or no issues remain. Produce only the final code changes and
supporting documents.

$ARGUMENTS

## Execution Boundary

This prompt orchestrates ten consecutive four-phase loops:

1. `analyze-code.md` — deep correctness analysis of the target module.
2. `create-design-plan.md` — design plan to resolve confirmed issues.
3. `create-implementation-spec.md` — implementation specification from the
   design plan.
4. `implement-spec.md` — implement the specification.

Within each loop, each phase must complete fully before the next phase
begins. The handoff from each phase to the next is the document produced:
findings → design plan → implementation spec → code changes.

After each loop completes, begin the next loop immediately using the
updated codebase as the new target. Each loop's analysis must re-examine
the full target module, not just the files changed in previous loops.

## Objective

Identify and resolve correctness issues in the target module through ten
consecutive deep-analysis loops. Each loop independently analyzes the
current state of the code, produces a design plan, converts it into a
concrete implementation specification, and implements the fixes — then
repeats on the improved codebase.

## Input

Path to the target module. Defaults to the project's core module if the
project has one. If no default module exists and no path is specified, ask
for the target module before proceeding.

Optionally, a scope constraint: specific packages, classes, or issue
references to narrow the analysis. The same scope constraint applies to
every loop.

## Loop Counter and Tracking

Maintain a loop counter from 1 to 10. At the start of each loop, report:

- **Loop N of 10** (where N is the current loop number).
- Number of loops remaining after this one.
- Total issues found and resolved across all previous loops.

## Per-Loop Phase 1: Analyze Code

Execute `analyze-code.md` for the target module.

### Deliverables

- Complete findings report with severity levels: Critical, High, Medium,
  Low.
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
  analysis found no correctness issues. Skip the remaining phases for this
  loop and proceed immediately to the next loop.

Report the number of issues selected for resolution in this loop before
moving to Phase 2.

## Per-Loop Phase 2: Design Plan

Execute `create-design-plan.md` with the selected findings from this loop's
Phase 1 as the problem statement.

### Deliverables

- Design plan document written to
  `.pi/plans/fix-<description>-loop<N>.md` covering all selected issues.
- Concrete approach, tradeoffs, test strategy, and acceptance criteria for
  each fix.

## Per-Loop Phase 3: Implementation Specification

Execute `create-implementation-spec.md` with the design plan from this
loop's Phase 2 as input.

### Deliverables

- Implementation specification document written to
  `.pi/plans/fix-<description>-loop<N>-spec.md` (or append `-spec` to the
  design plan path).
- Ordered implementation steps beginning with reproduction tests for every
  confirmed bug.
- Exact files, method signatures, behavior rules, and acceptance criteria.

## Per-Loop Phase 4: Implement Specification

Execute `implement-spec.md` with the implementation specification from this
loop's Phase 3 as input.

### Deliverables

- Reproduction tests that fail before the fix and pass after.
- The smallest focused source changes that resolve every selected issue.
- Passing test suite with no regressions.
- All acceptance criteria from the specification satisfied.

## Post-Loop Git Commit

After each loop completes (all four phases finish successfully), commit
the changes to git and push to the remote:

- Add all new, changed, and deleted files to the staging area.
- Create a signed-off commit:

    git commit -s -m "fix: Misc fix"

- Push the commit to the remote:

    git push

This ensures every loop's work is captured as an independent commit and
pushed before the next loop begins.

## Phase Handoffs

- The deliverable from each phase is the input to the next.
- Wait for each phase to complete before starting the next.
- Start each loop's Phase 1 by reading the target module files in their
  current state.

## Stop Conditions

Stop the entire process and report the blocker if:

- **Phase 1**: The target module cannot be located or its files cannot be
  read, or the code cannot be understood sufficiently to identify contracts.
- **Phase 2**: The selected issues are too vague to produce a concrete
  design, or required design decisions remain unresolved.
- **Phase 3**: The design plan is too incomplete to convert into a
  specification, or the expected failing behavior cannot be identified.
- **Phase 4**: The specification is ambiguous, contradicts project
  conventions, or describes unreproducible issues.

Early termination: If two consecutive loops find no issues (Phase 1
produces empty findings reports), stop early. Report the total number of
loops completed and summarize all fixes applied. Do not fabricate problems
to fill remaining loops.

## Completion Criteria

The entire ten-loop process is complete when:

- All ten loops have been attempted (or early termination was triggered).
- Each completed loop produced a findings report covering all source files
  in the target module at that point in time.
- Each loop with issues produced a design plan document, an implementation
  specification with reproduction tests and ordered steps, passing
  reproduction tests, the minimal source changes to resolve every selected
  issue, and a clean full test suite with no regressions.
- The final codebase has been validated by the last loop's Phase 1
  analysis, confirming no remaining Critical or High issues (and preferably
  no issues at all).
- Every document and code change follows the project's agent instructions
  for formatting, validation, and commit conventions.

## Final Report

In the final response, report for each loop that had issues:

- Loop number and the number of issues found and resolved.
- A summary of each fix applied in that loop.
- The paths of all documents produced (findings report, design plan,
  implementation specification).
- The changed source and test files.
- The validation commands run and their results.

Then report the aggregate summary:

- Total loops completed.
- Total issues found and resolved across all loops.
- Final state of the codebase (no remaining Critical/High issues, or clean
  analysis).
- All document paths and changed files across the entire process.
