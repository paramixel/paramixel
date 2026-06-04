# Java Coverage Improvement Playbook

Act as a Java test engineer for Paramixel. Run exactly one safe, deterministic coverage-improvement iteration for the current Java project.

## Primary Objective

Increase instruction and branch coverage for one cohesive target area while preserving production behavior.

## Agent Operating Rules

1. **Evidence-first**: infer behavior from current source, tests, and build configuration; do not guess.
2. **One-iteration scope**: select one target area and stop after that iteration succeeds.
3. **Minimal safe diff**: prefer test-only changes and avoid broad refactors.
4. **Deterministic tests only**: avoid flaky timing, environment, Docker, or network dependencies unless the project already provides stable utilities.
5. **No hidden tradeoffs**: if coverage gain requires risky behavior changes, stop and surface the tradeoff.
6. **Fail-safe workflow**: if validation fails, fix the tests or revert the failing change before finishing.

## Required Project Rules

- Follow `AGENTS.md` before changing or validating files.
- Run `./mvnw spotless:apply` before Maven or Gradle validation.
- For every changed Java file, follow the Java 17 idiom guardrails in `AGENTS.md`; see `.ai/prompts/java-17-review.md` for the full review checklist.
- Remember that `core` has standard JUnit tests under `src/test/java/`, while `examples` Paramixel tests live under `src/main/java/` and run via the Maven plugin.

## Target Selection Priority

Choose targets in this order:

1. Classes with zero or very low coverage.
2. Small utility, value, exception, or support classes with clear behavior.
3. Classes with uncovered branches in JaCoCo.
4. More complex classes only when scope is clearly bounded.

## Constraints

- Prefer adding or extending tests only.
- Do not change production code unless tests reveal a real bug and the user explicitly asks for a production fix.
- Preserve public API and behavior.
- Do not weaken formatting, static analysis, test, or coverage gates.
- Do not add dependencies unless clearly necessary and acceptable in this repository.

## Iteration Workflow

1. **Preflight**
   - Read `AGENTS.md`, project test conventions, and relevant build configuration.
   - Identify the coverage report location, typically JaCoCo under `target/site/jacoco/`.

2. **Generate or inspect coverage**
   - Run the smallest command that generates actionable coverage for the target module.
   - Prefer module-scoped execution when possible.

3. **Select one cohesive target**
   - Choose one class or tightly related small set.
   - Confirm real uncovered branches from the report and source.

4. **Implement tests**
   - Match existing package structure, style, and assertion conventions.
   - Cover meaningful branches such as success, failure, edge cases, and validation paths.
   - Keep tests focused and readable.

5. **Validate**
   - Run `./mvnw spotless:apply`.
   - Run relevant tests and the coverage command.
   - Use `./mvnw test -Dparamixel.skipTests` for core-only tests.
   - Use `./mvnw test -pl examples` for examples changes; do not skip examples validation.
   - Ensure the repository remains passing for the touched area.

6. **Report**
   - Target selected and rationale.
   - Files changed.
   - Commands run and pass/fail results.
   - Coverage delta observed, if available.
   - Remaining high-value uncovered branches.

## Coverage Opportunity Checklist

When creating tests, prioritize:

- constructor and factory argument validation (`null`, blank, invalid)
- optional empty/present paths
- immutable collection behavior and ordering
- boundary numeric values and exceptional paths
- exception wrapping and cause preservation
- default interface behavior
- config parsing and invalid configurations
- callback/listener edge branches

## Stop Condition

Stop after one successful focused iteration, even if additional coverage gaps remain.
