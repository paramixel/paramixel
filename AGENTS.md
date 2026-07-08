# Paramixel Agent Instructions

Provider-neutral instructions for coding agents working in the Paramixel repository. These rules apply regardless of LLM provider, model family, editor, or automation runtime.

---

## General Coding Principles

Reusable engineering discipline for coding agents. See
`.pi/prompts/coding-principles.md`.

---

## Structured Workflows

For complex tasks, use the structured prompt-driven workflows in `.pi/prompts/`.
Each prompt is self-contained, LLM-agnostic, and project-agnostic.

### Design-to-Implementation Workflow

| Step | Prompt | Purpose |
|------|--------|---------|
| 1. Resolve design decisions | `design-interview.md` | Structured interview resolving every open question |
| 2. Document approach | `create-design-plan.md` | Design plan with tradeoffs, API impact, and test strategy |
| 3. Create spec | `create-implementation-spec.md` | Step-by-step implementation instructions |
| 4. Implement | `implement-spec.md` | Execute the spec, tests-first |
| 5. Verify (optional) | `analyze-code.md` | Correctness and bug analysis pass |

### Standalone Workflows

| Prompt | Use When |
|--------|----------|
| `java-code-review.md` | Comprehensive engineering review of Java code |
| `java-code-coverage.md` | One focused, safe iteration to improve test coverage |
| `java-performance-review.md` | Performance recommendations from profile or benchmark data |
| `website-docs-reconciliation.md` | Update documentation to match current implementation |
| `planning-workflow.md` | Plan file naming convention (reference only) |

See `.pi/prompts/README.md` for the full prompt index and workflow diagrams.

---

## Paramixel-Specific Rules

The following rules are specific to the Paramixel repository and its build system.

## Critical Rules

- Before any Maven or Gradle validation/build command (`test`, `check`, `package`, `install`, `verify`, `javadoc`, etc.), run `./mvnw spotless:apply`. The only exception is an explicitly requested read-only formatting check with `./mvnw spotless:check`.
- If modifying code in `examples/`, validate with `./mvnw test -pl examples`; do **not** use `-DskipTests` or `-Dparamixel.skipTests` for that validation.
- Preserve Java 17 compatibility and existing public API/exception semantics unless the user explicitly asks for a breaking change.
- Do not weaken Spotless, strict Javadoc, PMD, test, or build configuration to make validation pass.
- Prefer the smallest safe change and report the commands run with pass/fail results.

## Standard Agent Workflow

1. Inspect the relevant source, tests, build files, and repository guidance before changing files.
2. Make the smallest focused change that satisfies the request.
3. Run `./mvnw spotless:apply` before Maven or Gradle validation.
4. Run the narrowest relevant validation command from the table below.
5. Prefer `./mvnw clean verify` when touching shared/core behavior or build configuration.
6. Summarize changed files, validation commands, results, and any remaining risks.

## Validation Commands

Run `./mvnw spotless:apply` first for validation/build commands unless the task is a read-only formatting check.

| Task | Command |
| --- | --- |
| Format code | `./mvnw spotless:apply` |
| Full Maven validation with static analysis | `./mvnw clean verify` |
| Core module JUnit tests only | `./mvnw test -Dparamixel.skipTests` |
| Paramixel tests in `examples/` (requires Docker) | `./mvnw test -pl examples` |
| Build without running any tests | `./mvnw clean install -DskipTests -Dparamixel.skipTests` |
| Check formatting only | `./mvnw spotless:check` |
| Check Javadoc only (Maven) | `./mvnw javadoc:javadoc` |
| Check Javadoc only (Gradle) | `./gradlew javadoc --no-daemon` |
| Build Maven project | `./mvnw clean install` |
| Build Gradle project | `./gradlew clean check --no-daemon` |

`-DskipTests` skips Surefire JUnit tests in standard test sources. `-Dparamixel.skipTests` skips the Paramixel Maven plugin execution in `examples/`. They are independent; use both only when intentionally skipping all tests.

## Module Structure

- `core/` — Main library, deploys to Maven Central
- `maven-plugin/` — Maven plugin for test execution, goal prefix `paramixel`, deploys to Maven Central
- `examples/` — Test classes and examples using Paramixel framework (with Testcontainers), not deployed
- `website/` — Docusaurus documentation site
- `assets/` — License header template, PMD rules

## Testing Rules

Paramixel tests in `examples/` live under `src/main/java/` (not `src/test/java/`) because they are executed by the Paramixel Maven plugin during the `test` phase, not by JUnit directly. The `core` module has standard JUnit 5 tests under `src/test/java/`.

See `examples/README.md` for the package hierarchy, `__ParamixelRunner__` convention, and Testcontainers integration details.

## Code Style and Java 17 Guardrails

- Spotless with Palantir Java Format runs automatically on `verify` phase.
- License header from `assets/license-header.txt` is required on all Java files.
- Run `./mvnw spotless:apply` before validation and before committing.
- Check formatting with `./mvnw spotless:check` when a read-only formatting check is needed.
- When modifying Java code, follow `.pi/prompts/java-code-review.md` and these guardrails:
  - Prefer clear, immutable post-build state over ad-hoc mutation.
  - Keep synchronization minimal and explicit.
  - Avoid novelty refactors; optimize only measurable hot paths.
  - Preserve null-safety contracts and existing exception semantics.

Repository guidance files reference these guardrails as the canonical source. See `.pi/prompts/java-code-review.md` for the full review checklist and modernization audit workflow. See `.pi/prompts/java-code-coverage.md` and `.pi/prompts/java-performance-review.md` for coverage and performance workflows.

## Javadoc

- Strict Javadoc is enforced in both Maven and Gradle builds.
- Maven: `doclint:all` + `-Werror` on `maven-javadoc-plugin` (runs during `package` phase).
- Gradle: `Xdoclint:all` + `Werror` on `javadoc` task (runs during `check`).
- Record compact constructors require their own `@param` tags, separate from record component `@param` tags.
- Missing `@param`, `@return`, or `@throws` tags will fail the build.
- The `examples/` module skips Javadoc generation entirely (`maven.javadoc.skip=true`).

## Planning

Plans go in `.pi/plans/`. See `.pi/prompts/planning-workflow.md` for naming conventions.

## Static Analysis

PMD runs on `verify` phase:

- PMD remains report-only (`failOnViolation=false`).
- Custom rules: `assets/pmd-ruleset.xml`.
- CI skips on Java 25: `-Dpmd.skip=true`.

## Commit Requirements

- DCO: All commits must be signed off with `git commit -s`.
- Conventional commit prefixes: `feature:`, `fix:`, `refactor:`, `chore:`, `performance:`, `polish:`.
- Dependency updates use scoped prefix: `chore(deps):` or `fix(deps):`.

## Release

Manual release with CI validation. See `RELEASING.md` for the release source of truth. Requires `~/.m2/settings.xml` (Maven Central credentials) and GPG signing key.

## JMH Benchmarks

Build and run JMH benchmarks for performance regression testing:

```bash
# Build benchmark fat JAR
./mvnw package -pl benchmarks -DskipTests -Dparamixel.skipTests

# Run all benchmarks
java -jar benchmarks/target/benchmarks.jar

# Run specific benchmark
java -jar benchmarks/target/benchmarks.jar SchedulerBenchmark

# Run with specific params
java -jar benchmarks/target/benchmarks.jar SchedulerBenchmark -p size=100,1000

# Quick mode (fewer iterations)
java -jar benchmarks/target/benchmarks.jar -f 1 -wi 3 -i 5
```

## Stress Testing

Run tests multiple times to check for flaky tests:

```bash
./scripts/stress-test.sh <number-of-iterations>
```

## Maven Version

Requires Maven 3.9+ (enforced by enforcer plugin).

## Java Compatibility

- Source/target/release: 17
- CI tests against Java 17, 21, 25

