Convert the approved design into a detailed implementation specification.

Do not implement anything yet.

## Prerequisite: Validate and Reproduce the Issue

Before writing the specification, ensure the issue can be reliably reproduced:
- Identify or write a minimal test that reproduces the bug (or demonstrates the missing feature).
- Run that test to confirm the failure (or missing behavior) exists on the current code.
- Only proceed to the specification once the reproduction is confirmed.

## Specification Contents

The specification must include:

### Tests to Add/Update (First)
- List the test file(s) and test method(s) that will validate the fix.
- Describe the test scenarios: happy path, edge cases, error conditions.
- Tests should be written **before** the implementation code (test-first approach).
- Where applicable, include both unit tests and integration tests.

### Ordered Implementation Steps
- Break the work into small, independently verifiable steps.
- Each step should have a clear completion criterion.

### Exact Files/Classes to Modify
- Full paths to source files that will be changed.

### New Classes/Interfaces to Create
- Fully qualified names, with a brief description of each.

### Method Signatures
- Exact method signatures (visibility, return type, name, parameters).

### Behavior Rules
- Detailed behavior for each changed or new method.
- Contractual obligations (preconditions, postconditions, invariants).

### Error Handling
- How invalid inputs, system errors, and edge cases are handled.
- Exception types and messages.

### Concurrency/Lifecycle Rules (if relevant)
- Thread-safety guarantees, locking strategy, resource cleanup.

### Acceptance Criteria
- The concrete conditions under which the implementation is considered complete.
- Must include a passing test suite as the primary acceptance gate.
