# Coding Principles

Reusable, project-agnostic engineering discipline for coding agents working on
Java repositories. This is a reference document, not an executable
workflow prompt, so it intentionally omits `$ARGUMENTS`.

These principles apply regardless of LLM provider, model family, editor, IDE,
or shell.

## 1. Read Before You Write

Before editing anything:

- Read the files you are about to modify.
- Read nearby tests and repository guidance.
- Understand existing naming, structure, exception handling, validation, and
  compatibility patterns.
- Do not infer behavior from file names alone.

## 2. Think Before You Code

- Restate the concrete goal.
- Identify the smallest safe change.
- State assumptions when the request is underspecified.
- Ask for clarification or report a blocker when assumptions would be risky.

## 3. Simplicity

- Prefer direct, boring, maintainable changes.
- Do not introduce abstractions for a single use case.
- Do not add dependencies unless the repository or user explicitly accepts them.
- Preserve existing architecture unless the task is explicitly architectural.

## 4. Surgical Changes

- Touch only files required by the task.
- Match the style of the file being edited.
- Clean up only artifacts caused by your change.
- Do not reformat unrelated code.

## 5. Verification

- For bugs, write or update a test that reproduces the behavior before fixing it.
- Run validation commands discovered from repository guidance.
- Do not claim validation passed unless it was run and passed.
- If validation cannot be run, say why and identify the command that should run.

## 6. Goal-Driven Execution

Translate vague tasks into verifiable outcomes:

- "Fix the bug" means reproduce it, make the smallest fix, and validate it.
- "Add a feature" means define expected behavior, tests, implementation, and
  acceptance criteria.
- "Refactor" means preserve externally observable behavior while improving the
  stated internal quality.

## 7. Debugging

- Read the full error or failure message.
- Reproduce before changing behavior.
- Change one cause at a time.
- Prefer evidence over guesses.
- Keep suspicious but unconfirmed observations separate from confirmed findings.

## 8. Dependencies

- Do not add, remove, or upgrade dependencies without explicit scope.
- Prefer standard library and existing project dependencies.
- Preserve lockfiles and generated dependency metadata unless dependency changes
  are in scope.

## 9. Communication

- Be concise and specific.
- Report changed files and validation results.
- Report assumptions and blockers separately.
- Do not hide uncertainty.

## 10. Common Failure Modes

Avoid:

1. Implementing before reading the code.
2. Solving a broader problem than requested.
3. Treating style preferences as correctness bugs.
4. Claiming tests passed without running them.
5. Changing public API or exception contracts unintentionally.
6. Applying personal style instead of repository style.
7. Ignoring null or edge-case behavior.
8. Hiding unrelated pre-existing failures.
