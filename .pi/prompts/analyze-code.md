# Code Analysis

Analyze the target module for correctness issues and potential bugs.
Produce findings only. Do not write or modify any code.

$ARGUMENTS

## Objective

Identify runtime bugs, logic errors, race conditions, resource leaks,
exception-handling problems, and API contract violations in the target
module. Report every finding with its location, impact, and a suggested fix.

## Input

Paths to the source files, interfaces, tests, and build configuration of
the target module. An issue reference or bug report may provide additional
context.

If the target module cannot be located or its files cannot be read, abort
and report the blocker.

## Prerequisites

Before performing the analysis:

- Read every source file, interface, test file, and build configuration
  file in the target module.
- Identify the module's documented contracts, public API semantics, and
  exception guarantees.
- Understand the concurrency model, lifecycle management, and error-
  propagation patterns in use.
- Only proceed to the analysis once the codebase is understood.

## Stop Conditions

Abort and report the blocker if:

- The target module cannot be located or its files cannot be read.
- The codebase cannot be understood sufficiently to identify contracts and
  semantics.

Do not fabricate findings when evidence is missing.

## Deliverables

### Scope

Include only issues with plausible correctness impact:

- Runtime bugs, logic errors, edge cases, and incorrect assumptions.
- Race conditions, resource leaks, and exception-handling problems.
- Invalid state transitions, concurrency issues, and API contract violations.
- Nullability issues, boundary conditions, and test coverage gaps that could
  hide real bugs.
- Issues that could cause incorrect behavior, hangs, crashes, data loss,
  skipped execution, duplicate execution, inconsistent results, or misleading
  success or failure reporting.
- Any place where behavior may differ from the documented contract.

### Exclusions

Do not include:

- Design preferences, architecture rewrites, or naming suggestions.
- Formatting, style, or documentation polish.
- Report, presentation, or UI improvements unless tied to a concrete
  correctness bug.
- "Nice to have" improvements.
- Performance suggestions unless they expose a correctness, deadlock,
  starvation, or resource-exhaustion bug.
- Broad redesigns or recommendations that cannot be tied to an observable
  incorrect behavior.

### Severity Levels

Check the project's agent instructions for defined severity levels. If
none are defined, use: Critical, High, Medium, Low.

### Output Format

1. **Summary** of the most serious potential bugs.
2. **Detailed findings**, grouped by severity: Critical, High, Medium, Low.
   For each finding include:
   - File and method or class.
   - What the potential bug is.
   - Why it is a bug, not a design preference.
   - A minimal scenario that could trigger it.
   - Suggested fix.
   - Suggested test case.
3. **Needs confirmation**: findings that look suspicious but lack sufficient
   evidence to classify as a bug. Explain what evidence is missing.

## Completion Criteria

The analysis is complete when:

- Every source file, interface, and test in the target module has been
  inspected.
- Every finding is tied to a specific file and method or class.
- Every finding describes an observable incorrect behavior.
- No design preferences, style suggestions, or broad redesigns are included.
