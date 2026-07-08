# Implement Specification

Implement the referenced implementation specification exactly. Do not deviate
from its scope.

## Objective

Execute every required step in the specification: write reproduction or
missing-behavior tests, implement the scoped fix or feature, run validation, and
verify acceptance criteria.

## Input

$ARGUMENTS

Path or reference to the implementation specification to implement.

If the specification cannot be found, report the blocker before continuing.

## Execution Boundary

This is an implementation-capable prompt.

- Modify only files required by the specification.
- Do not introduce unrelated refactors, style changes, dependency changes,
  generated-file changes, release actions, commits, or pushes unless explicitly
  required by the specification or user.
- Use repository-discovered build, test, format, and validation commands.
- Do not use network access, dependency downloads, browser actions, or editor
  actions unless the user or repository guidance explicitly requires them.

## Required Preflight

Before writing code:

- Read the implementation specification in full.
- Confirm tests to write, files to touch, method signatures, behavior rules,
  exception handling, concurrency/lifecycle requirements, and acceptance
  criteria.
- Read relevant source files, tests, repository guidance, and build/configuration
  files.
- Identify validation commands from repository guidance.
- Resolve every ambiguity before editing.

## Stop Conditions

Abort and report the blocker if:

- The specification cannot be found or is incomplete.
- Any required file, behavior rule, signature, or validation command is
  ambiguous.
- The specification conflicts with repository architecture or conventions.
- The reproduction or missing-behavior test cannot be written as specified.
- A validation failure appears unrelated to the scoped change and cannot be
  safely fixed within the specification.

## Workflow

1. **Write the specified test first.** Add or update the reproduction or
   missing-behavior test exactly as described.
2. **Confirm expected pre-fix behavior.** Run the narrowest repository-discovered
   validation command for the new test. If it does not fail for the expected
   reason, stop and investigate.
3. **Implement the smallest scoped change.** Follow the ordered implementation
   steps and signatures exactly.
4. **Re-run narrow validation.** Confirm the new or updated test passes.
5. **Run relevant regression validation.** Use repository-discovered commands.
6. **Apply required formatting.** Use the repository's formatting guidance.
7. **Verify acceptance criteria.** Confirm every criterion in the specification.

## Rules

### Change Discipline

- Keep changes minimal and focused on the specification.
- Preserve public API, compatibility, exception contracts, and existing behavior
  unless the specification explicitly changes them.
- Remove only imports, variables, or code made unused by your scoped change.
- Do not clean up unrelated pre-existing issues.

### Testing Discipline

- Test behavior, not implementation details.
- For bugs, keep the reproduction test as the first validation gate.
- If a required test cannot be written, stop and report why.

### Validation Discipline

- Run the narrowest relevant validation first, then broader validation required
  by repository guidance.
- Report every command run and pass/fail result.
- Do not claim validation passed unless it was run and passed.

### Error Recovery

- If your change causes a regression, either fix it within the specification or
  revert the scoped change and report the blocker.
- If existing unrelated tests fail, report them separately with evidence.

## Acceptance Criteria

Implementation is complete when:

- All specified tests are added or updated.
- The scoped implementation satisfies the behavior rules.
- Required validation passes or unrelated failures are documented.
- No unrelated files or behaviors are changed.

## Final Response

Report only:

- files changed;
- validation commands run with results;
- blockers, if any;
- assumptions, if any.
