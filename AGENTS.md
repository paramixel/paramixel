# Paramixel Agent Instructions

Provider-neutral instructions for coding agents working in the Paramixel repository. These rules apply regardless of LLM provider, model family, editor, or automation runtime.

---

## General Coding Principles

### 1. Read Before You Write

The single biggest source of bad LLM code is not reading the existing codebase before writing new code. Before writing anything:

- **Read the files you're about to modify.** Not skim. Read.
- **Look at how similar things are done elsewhere in the project.** Follow existing patterns for API routes, utility functions, naming conventions, and architectural decisions.
- **Check the imports.** They tell you what libraries this project actually uses. Don't introduce a new HTTP client if the project already uses one. Don't introduce a utility library if the standard library or an existing dependency covers it.
- **Look at the test files.** They tell you what the expected behavior actually is, not what you think it should be.

If you're not sure how something is done in this project, say so. "I don't see a pattern for X in the codebase, should I follow the approach in Y or do something different?" is always better than guessing.

### 2. Think Before You Code

Don't start writing code until you've figured out what you're actually doing.

- **State your assumptions.** If the user says "add validation," that could mean many things. Don't pick one silently. Say what you're assuming and let the user confirm.
- **Name the tradeoffs.** Almost every implementation choice has a tradeoff. Flag them. The user might say "actually I don't want that complexity."
- **If multiple approaches exist, present two or three with a recommendation.** Not five. Brief, with a clear preference.
- **If something is confusing, stop.** Don't fill confusion with plausible-sounding code. Say what's confusing and ask.

### 3. Simplicity

Write the minimum amount of code that solves the problem. Not the theoretical minimum — the minimum that actually solves this specific problem right now.

- **No premature abstraction.** If you need one thing, write one thing. Don't build a strategy pattern, factory, or framework for a single use case. "In case we need to" is not a requirement.
- **No speculative error handling.** Only handle errors that can actually happen. Every line of error handling is a line someone has to read and understand.
- **No unnecessary configurability.** Hardcode things unless there's a real reason to make them configurable. Every config option is a decision someone has to make.
- **No dead flexibility.** Don't create interfaces with one implementation. Don't add generic type parameters that are only ever instantiated with one type. The cost is cognitive overhead with zero benefit.

### 4. Surgical Changes

When editing existing code, your diff should be as small as possible.

- **Don't touch what you weren't asked to touch.** If you're fixing a bug in function A and notice function B has a weird variable name, leave it. Pre-existing issues are not your problem unless asked.
- **Match the existing style.** If the file uses `var`, use `var`. If it uses a particular naming convention, follow it. Consistency within a file beats your personal preference.
- **Clean up after yourself, not after others.** If your change makes an import or variable unused, remove it. But only if YOUR change caused it.
- **Don't reformat.** Don't change indentation, import order, or brace style on files you weren't asked to reformat. Use `./mvnw spotless:apply` for formatting.

The test: look at your diff. Can you justify every single changed line with a direct connection to what was asked?

### 5. Verification

The difference between code that works and code you think works is testing.

- **Write the test first when fixing bugs.** Before you fix anything, write a test that reproduces the bug. Watch it fail. Then fix the bug. Watch it pass.
- **Run existing tests before and after your changes.** If tests passed before and fail after, you broke something. If tests were already failing before your change, say so.
- **Don't write tests for the sake of writing tests.** A test that checks whether a constructor sets properties is worthless. Test behavior, not implementation.
- **If you can't write a test, say why.** "The database calls are tightly coupled to the business logic" is useful information that might signal a structural issue.

### 6. Goal-Driven Execution

Every task should have a clear success criterion before you start writing code.

Transform vague tasks into verifiable ones:
- "Add validation" → "reject inputs where email is missing or invalid, return 400 with a message, add tests for both cases."
- "Fix the bug" → "write a test that reproduces the reported behavior, make the test pass, verify existing tests still pass."
- "Improve performance" → "profile first, identify the bottleneck, fix that specific thing, measure again."

For multi-step work, state the plan before executing so the user can catch mistakes before you waste time implementing them.

### 7. Debugging

When something doesn't work, don't guess. Investigate.

- **Read the error message.** The whole thing, including the stack trace. A `NullPointerException` could mean a hundred different things. The message and trace tell you which one.
- **Reproduce first.** Before you change anything, make sure you can reproduce the problem. If you can't reproduce it, you can't verify your fix.
- **Change one thing at a time.** If you change three things and the bug goes away, you don't know which change fixed it.
- **Don't add workarounds without understanding the root cause.** A null check might prevent a crash, but the underlying bug is still there.
- **If you're stuck, say so.** "I've tried X and Y and neither worked. Here's what I'm seeing." is infinitely more useful than silently trying random things.

### 8. Dependencies

Don't add dependencies without thinking about it. Every dependency is code you don't control that becomes a permanent part of the project.

Before adding a dependency:
- Can you do this with what's already in the project?
- Can you do this with the standard library?
- Is this dependency actively maintained?
- How big is it?

When you do add a dependency, say why.

### 9. Communication

- **Say what you did and why.** Don't just dump a code block. Explain the motivation.
- **Flag concerns proactively.** "This works but it makes a database call for every item. If the list gets large this will be slow. Want me to batch it?"
- **Be precise about uncertainty.** "I'm not sure if this library supports streaming responses" is useful. "I think this should work" is not.
- **Don't explain things the user already knows.** Match your explanation level to their demonstrated knowledge.
- **Write specific commit messages.** "Fix bug" is useless. "Fix null pointer in user lookup when email contains uppercase chars" tells the next person exactly what happened.

### 10. Common Failure Modes

If you catch yourself doing any of these, stop and reconsider:

1. **The Kitchen Sink.** Asked to add one feature, you restructure half the codebase. Don't. Do the one thing.
2. **The Wrong Abstraction.** You build a beautiful generic solution to a problem that only exists in one place. Duplication is far cheaper than the wrong abstraction.
3. **The Invisible Decision.** You make an architectural choice without flagging it. Hard-to-reverse decisions should be surfaced.
4. **The Optimistic Path.** You handle the happy path perfectly and ignore everything else. Think about what happens when the API returns 500, the file doesn't exist, or the input is empty.
5. **The Knowledge Hallucination.** You confidently use an API that doesn't exist or a parameter that was removed. If you're not sure, check the docs or source code.
6. **The Style Drift.** You write code in your preferred style instead of matching the project. Match the codebase, not your preferences.
7. **The Runaway Refactor.** You start fixing one thing, it touches another, and twenty minutes later you've changed 15 files. If a fix is cascading, stop and tell the user.

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

