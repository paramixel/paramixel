# Design Plan

Produce a design plan document for the given problem. Produce only the
design plan.

$ARGUMENTS

## Execution Boundary

This prompt is for design-plan creation only.

- Do not write code.
- Do not implement anything.
- Do not create or modify tests.
- Do not modify source files, build files, configs, scripts, generated
  files, or unrelated documentation.
- Do not run build, test, formatting, validation, packaging, or release
  commands.
- You may inspect the repository by reading files and running read-only
  search/listing commands.
- The only permitted write is the requested design plan document.
- After writing the design plan document, stop.

## Objective

Document the approach, tradeoffs, API impact, concurrency model, test
strategy, and acceptance criteria for a proposed change. The plan must be
concrete enough that a reviewer can approve or reject the approach and an
implementer can convert it into an implementation specification.

## Input

A concrete problem statement, issue reference, or description of the
feature or bug. The input may also be a resolved design from
`design-interview.md`.

Optionally, the input may include paths to relevant design documents,
specifications, related issues, source files, or test files.

If the problem statement is too vague to produce a design (for example,
"make it faster"), ask for a concrete, verifiable problem description
before continuing.

If the problem is concrete but major design decisions remain unresolved,
do not invent answers. In an interactive runtime, ask for the missing
decision. Otherwise, stop and recommend resolving the open decisions with
`design-interview.md` before creating the design plan.

## Output

Write the plan to the file path specified by the user. If no path is
specified, write exactly one plan to:

`.pi/plans/<action>[-<number>]-<description>.md`

where:

- `<action>` is one of `fix`, `feature`, `refactor`, `chore`, or `polish`.
- `<number>` is an issue or ticket number, if available.
- `<description>` is a brief lowercase summary with words separated by
  dashes.

Use the user-specified path when supplied. If deriving the path and the
derived file already exists, choose a more specific non-conflicting
description or report a blocker. Do not overwrite an existing file unless
the user explicitly requested that path or replacement.

Do not write or update any other file.

## Prerequisites

Before proposing a design, use read-only repository inspection to:

- Read the relevant source files, interfaces, and tests in the affected area.
- Identify the existing patterns, naming conventions, and architectural
  decisions in play.
- Identify the relevant build or configuration files, if they constrain
  the design.
- Note any constraints that must be preserved: language or runtime
  version compatibility, public API semantics, exception contracts, and
  null-safety contracts.
- Identify existing API, error-handling, concurrency, and lifecycle
  patterns that the design must follow.
- Identify the likely test strategy and validation commands future
  implementers should use, without running those commands while creating
  the design plan.
- Only proceed to the design once the current code is understood.

## Stop Conditions

Abort and report the blocker if:

- The problem statement is too vague to produce a concrete, reviewable
  design.
- Repository inspection reveals the problem is already solved.
- The affected code or current behavior cannot be located or understood
  sufficiently.
- Required design decisions remain unresolved and cannot be inferred from
  the repository.
- The proposed design would conflict with documented repository
  constraints.
- The requested output path is ambiguous, outside the allowed write
  boundary, or cannot be written.
- Writing the plan would require modifying anything other than the design
  plan file.

Do not invent design elements without evidence from the codebase.

## Deliverables

The design plan must include the following sections.

### Problem Statement

- The problem being solved, in concrete terms.
- How the problem manifests today: observable behavior, missing capability,
  or performance gap.

### Goals and Non-Goals

- What the design is intended to accomplish.
- What is explicitly out of scope.

### Assumptions

- Assumptions inferred from the problem statement, repository, or existing
  design documents.
- Any assumption that requires reviewer confirmation.

### Relevant Existing Code

- Classes, interfaces, and test files in the affected area.
- Key patterns and contracts the design must follow.
- Existing public API surface that must remain backward-compatible.

### Proposed Design

- The approach: what changes, what stays the same, what is new.
- Files and classes to modify or create.
- Key method signatures and data flow.
- Component responsibilities and how they compose.

### Alternatives Considered

- At least one alternative approach, with reasoning for why it was not chosen.
- Tradeoffs between the proposed design and each alternative.

### API Impact and Backward Compatibility

- Whether public API, method signatures, or exception contracts change.
- Migration path for existing callers, if any.
- New dependencies, if any, with justification.

### Risks and Tradeoffs

- Complexity introduced, performance implications, maintenance burden.
- What could go wrong with this approach and how it is mitigated.
- Areas where the design is deliberately extensible or not.

### Concurrency and Lifecycle

Include if relevant. If not relevant, state that explicitly and omit the
section.

- Thread-safety guarantees, locking strategy, resource cleanup.
- Object lifecycle: creation, initialization, shutdown.

### Test Strategy

- Types of tests required: unit, integration, end-to-end.
- Test scenarios: happy path, edge cases, error conditions.
- Whether existing tests need to be updated.
- Validation commands future implementers should run, without running them
  during design-plan creation.

### Open Questions

Include only non-blocking questions, if any. Questions that affect the
implementation approach are blockers and must trigger a stop condition
instead.

### Implementation Handoff

- State whether a separate implementation specification should be created
  before coding begins.
- State that implementation must wait for reviewer sign-off on the design
  approach.

### Acceptance Criteria

- Concrete conditions under which the design plan is complete.
- Must include reviewer sign-off on the approach before implementation
  begins.

## Completion Boundary

Once the design plan file has been written, stop.

Do not continue into implementation specification, test creation, source
changes, formatting, validation, cleanup, or implementation tasks.

In the final response, report only:

- The design plan path written.
- Any blockers or assumptions.
