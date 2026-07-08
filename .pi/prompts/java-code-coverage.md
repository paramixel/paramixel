# Java Coverage Improvement Playbook

Improve test coverage through one safe, focused, deterministic iteration.

## Objective

Add or improve tests for one cohesive target while preserving production
behavior and repository conventions.

## Input

$ARGUMENTS

A target repository, module, package or class, file, coverage report, or scope
constraint. If no target is supplied, inspect repository guidance and coverage
output to choose one small high-value target.

## Execution Boundary

This is a test-focused implementation prompt.

- Prefer adding or updating tests only.
- Do not change production code unless tests expose a real bug and the user
  explicitly requests a production fix.
- Do not add dependencies unless explicitly accepted by the repository or user.
- Do not require browser/editor actions, network access, commits, or pushes.
- Stop after one focused successful iteration.

## Required Preflight

- Read repository guidance for testing, formatting, validation, and dependencies.
- Identify the repository's test and coverage commands from documentation or
  build files.
- Read the target source and nearby tests before editing.
- Identify existing assertion libraries and test style; use what the repository
  already uses.

## Coverage Command Guidance

Use repository-discovered commands. Generic examples for Java projects include the repository's Maven or Gradle test and coverage tasks. Adapt them to repository guidance.

Treat any language command in this prompt as an example, not a universal
requirement.

## Target Selection Priority

Choose one target in this order:

1. Low or zero coverage code with clear behavior.
2. Uncovered branches in small utility or configuration code.
3. Error/exception paths and edge cases.
4. Boundary values, empty values, and null handling.
5. More complex code only when the target is clearly bounded.

## Iteration Workflow

### 1. Establish Baseline

Run the narrowest repository-discovered coverage or test command needed to
identify the target. Record current coverage or explain why coverage cannot be
measured.

### 2. Select One Cohesive Target

Choose one function, method, class, file, or tightly related set of behavior.
Confirm real uncovered behavior from source, tests, and coverage evidence.

### 3. Implement Tests

- Add to existing tests when possible.
- Match package/class structure and repository test style.
- Cover meaningful behavior: success, exception paths, edge cases, boundary
  values, and null behavior.
- Avoid tests that only assert implementation details.

### 4. Validate

Run the narrowest relevant test first, then broader validation required by
repository guidance. Run coverage again if feasible to confirm the delta.

### 5. Report

Report:

- target selected and rationale;
- files changed;
- commands run with pass/fail results;
- coverage delta, or why it could not be measured;
- remaining high-value uncovered behavior in the target area.

## Stop Conditions

Stop if:

- No safe bounded test target can be identified.
- Required repository context cannot be read.
- Improving coverage requires production changes not explicitly requested.
- Validation failures are unrelated and cannot be safely handled in scope.
- One focused coverage iteration has completed.

## Final Response

Report only:

- target selected and rationale;
- files changed;
- validation and coverage commands run with results;
- coverage delta, or why it could not be measured;
- blockers and assumptions, if any.
