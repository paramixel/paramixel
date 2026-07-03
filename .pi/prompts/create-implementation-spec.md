# Implementation Specification

Convert a design plan into a detailed implementation specification.
Produce only the specification document.

## Execution Boundary

This prompt is for specification creation only.

- Do not write code.
- Do not implement anything.
- Do not create or modify tests.
- Do not modify source files, build files, docs, configs, scripts, or
  generated files.
- Do not run build, test, formatting, validation, or packaging commands.
- You may inspect the repository by reading files and running read-only
  search/listing commands.
- The only permitted write is the requested implementation specification
  document.
- After writing the specification document, stop.

## Objective

Produce step-by-step implementation instructions — tests, file changes,
method signatures, behavior rules, error handling — concrete enough that
an implementer can execute them without making design decisions.

For bugs and issues, the specification must require the implementer to
recreate the problem with a failing test first, confirm the expected
failure, and only then implement the fix.

## Input

Path or reference to the design plan to convert into a specification.
Optionally, an issue reference providing additional context.

If the design plan cannot be found or is incomplete, report the blocker
before continuing.

## Output

Write the specification to the file path specified by the user. If no path
is specified, write to `.pi/plans/<action>-<description>.md` using the
same naming convention as the design plan.

Do not write or update any other file.

## Specification Planning Prerequisites

Before writing the specification, inspect enough context to describe the
reproduction-first implementation workflow. Do not execute that workflow.

For bugs and issues, identify in the specification:

- The minimal test file and test method the implementer should add or
  update to reproduce the bug.
- The exact scenario the test should cover.
- The command the implementer should run to observe the expected failure.
- The expected pre-fix failure mode: assertion failure, exception, error
  message, incorrect result, or missing behavior.
- The source area the implementer should fix after confirming the failing
  test.

For features without a bug, describe the test that should demonstrate the
missing behavior before implementation begins.

## Stop Conditions

Abort and report the blocker if:

- The design plan cannot be located or is too incomplete to convert.
- The issue description is too vague to define a concrete reproduction
  test or missing-behavior test.
- The expected failing behavior cannot be identified from the design plan,
  issue, source, or existing tests.
- The requested output path is ambiguous or cannot be written.

## Deliverables

The specification must include the following sections.

### Tests to Add or Update

List every test file and test method that will validate the work. Describe
the test scenarios: happy path, edge cases, error conditions.

For bugs and issues, the first test listed must be the minimal
reproduction test. Include the file path, test class, test method name,
setup, action, assertion, and expected pre-fix failure. State that the
implementer must not start implementation until this test fails for the
expected reason.

For features, identify the test that demonstrates the missing behavior
before implementation. Where applicable, include both unit tests and
integration tests.

### Ordered Implementation Steps

Break the work into small, independently verifiable steps. Each step must
have a clear completion criterion.

For bugs and issues, the ordered steps must begin with:

1. Add or update the minimal reproduction test.
2. Run the narrowest relevant test command and confirm it fails for the
   expected reason.
3. Implement the smallest source change that fixes the failure.
4. Re-run the reproduction test and relevant regression tests.
5. Apply formatting and broader validation required by project guidance.

These are instructions for the future implementer. Do not execute them
while creating the specification.

### Exact Files and Classes to Modify

Full paths to every source file that will be changed.

### New Classes and Interfaces to Create

Fully qualified names with a brief description of each.

### Method Signatures

Exact signatures for every method: visibility, return type, name,
parameters.

### Behavior Rules

Detailed behavior for each changed or new method. Contractual obligations:
preconditions, postconditions, invariants.

### Error Handling

How invalid inputs, system errors, and edge cases are handled. Exception
types and messages.

### Concurrency and Lifecycle

Include if relevant. If not relevant, state that explicitly and omit the
section.

- Thread-safety guarantees, locking strategy, resource cleanup.

### Acceptance Criteria

Concrete conditions under which the implementation is complete. Must
include a passing test suite as the primary acceptance gate.

## Completion Boundary

Once the specification file has been written, stop.

Do not continue into test creation, source changes, implementation,
formatting, validation, or cleanup tasks.

In the final response, report only:

- The specification path written.
- Any blockers or assumptions.
