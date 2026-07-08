# Design Interview

Conduct a structured design interview to resolve design decisions before a
plan is written.

## Objective

Arrive at a decision-complete, self-consistent design state for the problem in
`$ARGUMENTS`. Resolve dependent decisions in order and avoid open-ended
brainstorming.

## Input

$ARGUMENTS

A concrete problem statement, issue reference, feature request, or bug report.
Optionally include paths to existing designs, specifications, source files,
tests, or related issues.

If the input is too vague to scope the interview, ask for a concrete problem
statement before continuing.

## Execution Boundary

This is a read-only interview prompt.

- Do not write code.
- Do not create or modify tests.
- Do not modify source files, build files, configs, generated files, or
  documentation.
- Do not run build, test, formatting, packaging, release, commit, push, network,
  browser, or editor actions.
- You may inspect the repository by reading files and using read-only search or
  listing commands.

## Required Repository Inspection

Before asking design questions:

- Read referenced design documents, specifications, source files, and tests.
- Identify current architecture, naming, API, exception handling, and
  validation patterns.
- Answer any question that can be resolved from repository evidence instead of
  asking the user.
- State assumptions only when they are low-risk and clearly marked.

## Interview Rules

- Ask only decision-relevant questions.
- Walk decisions in dependency order.
- Include a recommended answer with each question and explain why it is
  recommended.
- Keep a running design state from prior answers.
- Do not invent answers for unresolved decisions that affect implementation.

### Interaction Mode

If the runtime supports multi-turn interaction, ask exactly one question and
then stop. Do not include future questions or a full interview outline.

If the runtime does not support multi-turn interaction, produce all questions as
a structured list in dependency order. Mark questions that depend on earlier
answers and include a recommended answer for each.

## Topics to Cover

Skip topics that are not relevant. For skipped mandatory topics, state `Not
applicable`.

### Scope and Boundaries

- Problem being solved.
- Explicit non-goals.
- Affected packages or classes and files.

### Data and State

- Inputs, outputs, intermediate state, and invariants.
- Compatibility and migration constraints.

### API and Contracts

- Public APIs, interfaces, signatures, and behavior contracts.
- Backward compatibility requirements.

### Error Handling

- Expected failure modes.
- Exception types, messages, wrapping, propagation, or recovery behavior.

### Concurrency and Lifecycle

- Thread, executor, synchronization, resource, and lifecycle requirements.
- Startup, shutdown, cleanup, ownership, and cancellation rules.

### Testing and Validation

- Unit, integration, regression, and edge-case scenarios.
- Repository-discovered validation commands.

## Stop Conditions

Abort and report the blocker if:

- The problem statement is too vague to scope.
- Repository inspection shows the requested problem is already solved.
- Required context cannot be found.
- A design branch cannot be resolved from repository evidence or user input.
- The requested design conflicts with documented repository constraints.

## Completion Criteria

The interview is complete when:

- Every implementation-affecting design branch has an explicit answer.
- Assumptions, non-goals, compatibility constraints, and validation strategy are
  recorded.
- No open questions remain that would block a design plan.

## Final Response

Return either:

- the next single interview question with a recommended answer; or
- a compact resolved-design summary suitable for `create-design-plan.md`; or
- a blocker with the exact missing information.
