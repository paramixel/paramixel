Analyze the `core` module for potential bugs and correctness issues only.

Scope:

* Focus on runtime bugs, logic errors, edge cases, incorrect assumptions, race conditions, resource leaks, exception-handling problems, invalid state transitions, concurrency issues, API contract violations, nullability issues, boundary conditions, and test coverage gaps that could hide real bugs.
* Include issues that could cause incorrect behavior, hangs, crashes, data loss, skipped execution, duplicate execution, inconsistent results, or misleading success/failure reporting.
* Pay special attention to scheduler/execution flow, lifecycle behavior, error propagation, cancellation/interruption handling, parallel execution, shared mutable state, and any places where behavior may differ from the documented contract.

Do not include:

* Design preferences
* Architecture rewrites
* Naming suggestions
* Formatting/style comments
* Documentation polish
* HTML report format ideas
* UI/reporting improvements unless there is a concrete correctness bug in the current behavior
* “Nice to have” improvements
* Performance suggestions unless they expose a correctness, deadlock, starvation, or resource-exhaustion bug

Output format:

1. Summary of the most serious potential bugs
2. Detailed findings, grouped by severity:

    * Critical
    * High
    * Medium
    * Low
3. For each finding include:

    * File and method/class
    * What the potential bug is
    * Why it is a bug, not a design preference
    * A minimal scenario that could trigger it
    * Suggested fix
    * Suggested test case
4. If something looks suspicious but is not clearly a bug, put it in a separate section called “Needs confirmation” and explain what evidence is missing.

Be strict: only report items that have a plausible correctness impact. Do not recommend broad redesigns.

Treat this as a defect audit, not an architecture review. If a recommendation cannot be tied to an observable incorrect behavior, omit it.