# Paramixel Agent Instructions

## Build & Test Commands

**Always run `./mvnw spotless:apply` before any `./mvnw` or `./gradlew` build step.** Formatting issues will cause builds to fail; applying Spotless first prevents this.

```bash
# Format code (REQUIRED before any build)
./mvnw spotless:apply

# Full build with static analysis
./mvnw clean verify

# Run core module JUnit tests only (skips Paramixel tests in examples/)
./mvnw test -Dparamixel.skipTests

# Run Paramixel tests in examples/ (requires Docker)
./mvnw test -pl examples

# Build without running any tests
./mvnw clean install -DskipTests -Dparamixel.skipTests

# Check formatting
./mvnw spotless:check

# Check javadoc only (Maven)
./mvnw javadoc:javadoc

# Check javadoc only (Gradle)
./gradlew javadoc --no-daemon

# Build Maven project
./mvnw clean install

# Build Gradle project
./gradlew clean check --no-daemon
```

**`-DskipTests`** skips Surefire JUnit tests (core module). **`-Dparamixel.skipTests`** skips the Paramixel Maven plugin execution (examples/ module). They are independent — use both to skip all tests. If modifying code in `examples/`, do NOT use `-Dparamixel.skipTests` or `-DskipTests` or you will skip the tests you're trying to validate.

## Module Structure

- `core/` — Main library, deploys to Maven Central
- `maven-plugin/` — Maven plugin for test execution, goal prefix `paramixel`, deploys to Maven Central
- `examples/` — Test classes and examples using Paramixel framework (with Testcontainers), not deployed
- `website/` — Docusaurus documentation site
- `assets/` — License header template, PMD rules

## Test Location Quirk

Paramixel tests in `examples/` live under `src/main/java/` (not `src/test/java/`) because they are executed by the Paramixel Maven plugin during the `test` phase, not by JUnit directly. The `core` module has standard JUnit 5 tests under `src/test/java/`.

See `examples/README.md` for the package hierarchy, `__ParamixelRunner__` convention, and Testcontainers integration details.

## Code Style

- Spotless with Palantir Java Format runs automatically on `verify` phase
- License header from `assets/license-header.txt` required on all Java files
- Run `./mvnw spotless:apply` before any build step and before committing
- Check formatting with `./mvnw spotless:check`
- When modifying Java code, follow the guidelines in `.ai/prompts/java-17-idioms.md` and the guardrails below (## Java 17 Idiom Guardrails)

## Java 17 Idiom Guardrails

These guardrails apply to all Java code changes in this project:

- Prefer clear, immutable post-build state over ad-hoc mutation.
- Keep synchronization minimal and explicit.
- Avoid novelty refactors; optimize only measurable hot paths.
- Preserve null-safety contracts and existing exception semantics.

Prompt files reference this section as the canonical source. See `.ai/prompts/java-17-idioms.md` for the full review checklist and modernization audit workflow.

## Javadoc

- Strict javadoc is enforced in both Maven and Gradle builds
- Maven: `doclint:all` + `-Werror` on `maven-javadoc-plugin` (runs during `package` phase)
- Gradle: `Xdoclint:all` + `Werror` on `javadoc` task (runs during `check`)
- Record compact constructors require their own `@param` tags (separate from record component `@param` tags)
- Missing `@param`, `@return`, or `@throws` tags will fail the build
- The `examples/` module skips javadoc generation entirely (`maven.javadoc.skip=true`)

## Planning

Plans go in `.ai/plans/`.

## Static Analysis

PMD runs on `verify` phase:
- PMD remains report-only (`failOnViolation=false`)
- Custom rules: `assets/pmd-ruleset.xml`
- CI skips on Java 25: `-Dpmd.skip=true`

## Commit Requirements

- DCO: All commits must be signed off with `git commit -s`
- Conventional commit prefixes: `feature:`, `fix:`, `refactor:`, `chore:`, `performance:`, `polish:`
- Dependency updates use scoped prefix: `chore(deps):` or `fix(deps):`

## Release

Manual release with CI validation. See `RELEASING.md` for full details and `.ai/prompts/release.md` for an AI-oriented workflow. Requires `~/.m2/settings.xml` (Maven Central credentials) and GPG signing key.

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
