# Java Coverage Improvement Playbook

Run exactly one safe, deterministic coverage-improvement iteration for
the current Java project.

## Objective

Increase instruction and branch coverage for one cohesive target area
while preserving production behavior. Stop after one successful iteration.

## Agent Operating Rules

1. **Evidence-first.** Infer behavior from current source, tests, and build
   configuration. Do not guess.
2. **One-iteration scope.** Select one target area and stop after that
   iteration succeeds.
3. **Minimal safe diff.** Prefer test-only changes. Avoid broad refactors.
4. **Deterministic tests only.** Avoid flaky timing, environment, Docker, or
   network dependencies unless the project already provides stable utilities
   for them.
5. **No hidden tradeoffs.** If coverage gain requires risky behavior changes,
   stop and surface the tradeoff.
6. **Fail-safe workflow.** If validation fails, fix the tests or revert the
   failing change before finishing.

## Project Context

- Read the project's agent instructions, test conventions, and build
  configuration before changing or validating files.
- Identify the format command, test commands, and coverage report location.
- Follow the project's language idiom guardrails from its agent instructions.
- Understand the project's test directory structure: standard JUnit tests,
  integration tests, plugin-driven tests, and so on.

## Target Selection Priority

Choose targets in this order:

1. Classes with zero or very low coverage.
2. Small utility, value, exception, or support classes with clear behavior.
3. Classes with uncovered branches in the coverage report.
4. More complex classes only when scope is clearly bounded.

## Constraints

- Prefer adding or extending tests only.
- Do not change production code unless tests reveal a real bug and the user
  explicitly asks for a production fix.
- Preserve public API and behavior.
- Do not weaken formatting, static analysis, test, or coverage gates.
- Do not add dependencies unless clearly necessary and acceptable in the
  repository.

## Iteration Workflow

### 1. Preflight

- Read the project's agent instructions, test conventions, and build
  configuration.
- Identify the coverage report location (for example, JaCoCo reports under
  the project's build output directory).

### 2. Generate or Inspect Coverage

- Run the smallest command that generates actionable coverage for the target
  module.
- Prefer module-scoped execution when possible.

### 3. Select One Cohesive Target

- Choose one class or a tightly related small set.
- Confirm real uncovered branches from the report and source.

### 4. Implement Tests

- Match the existing package structure, style, and assertion conventions.
- Cover meaningful branches: success, failure, edge cases, validation paths.
- Keep tests focused and readable.

### 5. Validate

- Run the project's format command.
- Run the relevant test commands for the target module.
- Ensure the repository remains passing for the touched area.

### 6. Report

Produce:

- Target selected and rationale.
- Files changed.
- Commands run with pass or fail results.
- Coverage delta observed, if available.
- Remaining high-value uncovered branches.

## Coverage Opportunity Checklist

When creating tests, prioritize:

- Constructor and factory argument validation (`null`, blank, invalid)
- Optional empty and present paths
- Immutable collection behavior and ordering
- Boundary numeric values and exceptional paths
- Exception wrapping and cause preservation
- Default interface behavior
- Config parsing and invalid configurations
- Callback and listener edge branches

## Stop Condition

Stop after one successful focused iteration, even if additional coverage
gaps remain.
