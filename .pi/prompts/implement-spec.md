# Implement Specification

Implement the implementation specification exactly. Do not deviate.
Do not add unrelated changes.

## Objective

Execute every step in the specification: write reproduction tests,
implement the fix or feature, run validation, and verify acceptance
criteria. Produce only the changes the specification describes.

## Input

Path or reference to the implementation specification to implement.

If the specification cannot be found, report the blocker before continuing.

## Prerequisites

Before writing any code:

- Read the implementation specification in full.
- Confirm you understand every section: tests to write, files to touch,
  method signatures, behavior rules, acceptance criteria.
- Identify the project's build, test, and formatting commands from the
  project's agent instructions or repository guidance.
- Do not begin implementation until the specification is fully understood
  and every ambiguity is resolved.

## Stop Conditions

Abort and report the blocker if:

- The specification cannot be found or is incomplete.
- Any part of the specification is ambiguous. Ask for clarification before
  implementing.
- The specification describes a change that contradicts the project's
  conventions or architecture.

## Workflow

1. **Write reproduction tests first.** Write the tests described in the
   specification that reproduce the bug or demonstrate the missing feature.
   If the project follows test-first methodology, these tests must fail
   against the current code. If the project does not follow test-first
   methodology, adapt accordingly but tests must still be specified before
   implementation steps.

2. **Run the reproduction tests.** Execute them using the project's test
   commands. If the tests pass unexpectedly, stop and investigate before
   continuing.

3. **Implement the fix or feature.** Make the smallest focused change that
   satisfies the specification. Follow the ordered implementation steps and
   method signatures exactly as written.

4. **Run the reproduction tests again.** Confirm they pass after the
   implementation.

5. **Run the full relevant test suite.** Execute the project's standard
   validation — formatting, static analysis, all relevant tests — to ensure
   no regressions.

6. **Verify acceptance criteria.** Confirm every acceptance criterion in
   the specification is met before declaring the work complete.

## Rules

### Change Discipline

- Do not introduce unrelated refactors, style changes, or "while I'm here"
  cleanups.
- Do not change public API, exception contracts, or method signatures
  beyond what the specification states.
- Keep changes minimal and focused on the specification.
- Remove only the imports, variables, or code that your change makes unused.

### Testing Discipline

- If the project follows test-first methodology, write tests before
  implementation code.
- Tests must cover happy path, edge cases, and error conditions as
  described in the specification.
- Do not weaken test assertions or skip tests to make validation pass.

### Validation Discipline

- Run the project's format command before any build or validation.
- Run the relevant test commands from the project's guidance before and
  after implementation.
- If pre-existing test failures exist, report them before starting. Do not
  silently fix them.

### Error Recovery

- If reproduction tests pass before the implementation, stop: the issue may
  not be reproducible, or the test may be incorrect.
- If a change causes unexpected test failures, revert to the last passing
  state and reassess before continuing.
- If stuck, report what was tried and what was observed rather than
  guessing.

## Acceptance Criteria

- All tests described in the specification pass.
- The full project test suite passes with no regressions.
- Every acceptance criterion in the specification is satisfied.
