# Implementation Specification

Convert a completed design plan into an implementation specification. Produce
only the specification artifact and the final response described below.

## Execution Boundary

This prompt is for implementation-specification creation only.

- Do not write production code.
- Do not create or modify tests.
- Do not modify source files, build files, configs, scripts, generated files, or
  unrelated documentation.
- Do not run build, test, formatting, validation, packaging, release, commit,
  push, network, browser, or editor actions.
- You may inspect the repository and design plan by reading files and using
  read-only search or listing commands.
- The only permitted write is the requested implementation specification.
- After writing the specification, stop.

## Objective

Create a deterministic, test-first implementation specification that tells a
future implementer exactly what to change, how to validate it, and when to stop.

## Input

$ARGUMENTS

A path or reference to a design plan. The input may include an issue reference,
requested output path, or additional repository context.

If the design plan cannot be found or is incomplete, report a blocker before
continuing.

## Output Path Rules

Use a user-specified path when supplied. Otherwise write to the same directory
as the design plan using the same base name with `-spec` appended before the
extension.

Do not overwrite an existing specification unless the user explicitly requested
replacement. Do not write or update any other file.

## Specification Planning Prerequisites

Before writing the specification:

- Read the design plan in full.
- Inspect enough source, tests, repository guidance, and build/configuration
  context to describe the implementation workflow accurately.
- Identify the narrowest reproduction or missing-behavior test to write first.
- Identify expected pre-fix behavior: assertion failure, panic, exception,
  incorrect result, missing behavior, or compile failure.
- Identify validation commands from repository guidance without running them.

## Stop Conditions

Abort and report the blocker if:

- The design plan cannot be located.
- The design plan is too incomplete to convert.
- The expected failing or missing behavior cannot be identified.
- Required files, signatures, behavior, or compatibility constraints are
  ambiguous.
- The requested output path is ambiguous, outside the allowed write boundary, or
  cannot be written.

## Deliverables

The specification must include these sections in order:

### Source Design Plan

Path to the design plan.

### Objective

Implementation objective and scope.

### Tests to Add or Update

Every test file and test case/function/class to add or update.

For bugs, list the minimal reproduction test first and include:

- file path;
- test name;
- setup;
- action;
- assertion;
- expected pre-fix failure;
- instruction not to start implementation until the test fails for the expected
  reason.

For features, identify the missing-behavior test that must fail or be absent
before implementation.

### Ordered Implementation Steps

Small, independently verifiable steps. For bugs and issues, the first steps
must be:

1. Add or update the minimal reproduction test.
2. Run the narrowest relevant test command and confirm the expected failure.
3. Implement the smallest source change that fixes the failure.
4. Re-run the reproduction test and relevant regression tests.
5. Apply formatting and broader validation required by repository guidance.

### Exact Files and Classes to Modify

Exact paths to files that will be changed.

### New Classes and Interfaces to Create

New methods, classes, and interfaces to create, or `Not applicable`.

### Method Signatures

Exact signatures for every changed or new method or constructor, including parameters, return values, and checked exceptions. Use `Not applicable` if no signatures change.

### Behavior Rules

Preconditions, postconditions, invariants, compatibility behavior, and edge
cases.

### Error Handling

Invalid inputs, system failures, exceptions, messages, wrapping, propagation,
and recovery behavior, or `Not applicable`.

### Concurrency and Lifecycle

Thread, executor, synchronization, resource, and lifecycle guarantees, cleanup, cancellation, and ownership
rules, or `Not applicable`.

### Acceptance Criteria

Concrete completion gates, including passing validation as the primary gate.

## Completion Boundary

After writing the specification file, stop. Do not continue into tests, source
changes, implementation, formatting, validation, or cleanup.

## Final Response

Report only:

- the specification path written;
- blockers, if any;
- assumptions, if any.
