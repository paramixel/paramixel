# Design Plan

Produce a design plan document for the given problem. Produce only the design
plan artifact and the final response described below.

## Execution Boundary

This prompt is for design-plan creation only.

- Do not write code.
- Do not implement anything.
- Do not create or modify tests.
- Do not modify source files, build files, configs, scripts, generated files, or
  unrelated documentation.
- Do not run build, test, formatting, validation, packaging, release, commit,
  push, network, browser, or editor actions.
- You may inspect the repository by reading files and using read-only search or
  listing commands.
- The only permitted write is the requested design plan document.
- After writing the design plan document, stop.

## Objective

Document the approach, tradeoffs, API impact, compatibility impact,
exception handling, concurrency/lifecycle model, test strategy, and
acceptance criteria for a proposed change. The plan must be concrete enough for
review and for conversion into an implementation specification.

## Input

$ARGUMENTS

A concrete problem statement, issue reference, resolved design interview, or
feature/bug description. The input may include paths to relevant design
documents, specifications, source files, tests, or related issues.

If the problem is too vague to produce a concrete, reviewable design, ask for a
specific verifiable problem before continuing.

## Output Path Rules

Write exactly one plan.

Use a user-specified path when supplied. Otherwise write to:

`.pi/plans/<action>[-<number>]-<description>.md`

where:

- `<action>` is `fix`, `feature`, `refactor`, `chore`, or `polish`.
- `<number>` is an issue or ticket number, if available.
- `<description>` is a brief lowercase dash-separated summary.

If deriving the path and the file already exists, choose a more specific
non-conflicting description or report a blocker. Do not overwrite an existing
file unless the user explicitly requested replacement.

The `.pi/plans/` directory is a local planning-artifact location. Create it if
needed. Do not write or update any other file.

## Required Repository Inspection

Before proposing a design:

- Read relevant source files, interfaces, tests, repository guidance, and build
  or configuration files.
- Identify existing patterns, naming conventions, architecture, APIs,
  exception handling, and validation practices.
- Identify constraints to preserve, including language/runtime version,
  compatibility, public API semantics, exception contracts, and null-safety
  or nullability contracts.
- Identify likely validation commands from repository guidance without running
  them.
- Proceed only after current behavior and constraints are understood.

## Stop Conditions

Abort and report the blocker if:

- The problem statement is too vague.
- Repository inspection reveals the problem is already solved.
- Affected code or current behavior cannot be located or understood.
- Required design decisions remain unresolved.
- The design would conflict with documented repository constraints.
- The output path is ambiguous, outside the allowed write boundary, or cannot be
  written.

## Deliverables

The design plan must include these sections in order:

### Problem Statement

Concrete problem and evidence from repository inspection.

### Goals and Non-Goals

Explicit in-scope and out-of-scope behavior.

### Assumptions

Assumptions required for the plan, or `None`.

### Relevant Existing Code

Exact files and methods, classes, and interfaces inspected. Include repository patterns that must
be preserved.

### Proposed Design

Concrete design, data flow, behavior rules, and affected components.

### Alternatives Considered

At least one alternative or `None` if no meaningful alternative exists.

### API Impact and Backward Compatibility

Public API, behavior, compatibility, migration, and deprecation impact, or `Not
applicable`.

### Error Handling

Expected failures and exception handling behavior, or `Not applicable`.

### Risks and Tradeoffs

Correctness, maintainability, compatibility, performance, and operational risks.

### Concurrency and Lifecycle

Thread, executor, synchronization, resource, and lifecycle implications, or `Not applicable`.

### Test Strategy

Tests to add/update and validation commands future implementers should run.
Do not run them while creating the design plan.

### Open Questions

Questions that do not block the plan, or `None`. Blocking questions belong in a
blocker response instead of the plan.

### Implementation Handoff

Files likely to modify, sequencing, and constraints for the implementation spec.

### Acceptance Criteria

Concrete conditions required for completion, including validation expectations.

## Completion Boundary

Once the design plan file has been written, stop. Do not continue into
implementation specification, tests, source changes, formatting, validation, or
cleanup.

## Final Response

Report only:

- the design plan path written;
- blockers, if any;
- assumptions, if any.
