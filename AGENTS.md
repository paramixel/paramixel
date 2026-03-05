# Paramixel — Agent Rules

## Specification

This project uses specification-oriented development. The authoritative system specification lives under `.specify/specs/system/`. Before implementing any feature, you MUST read the relevant spec files.

## Critical Rules

- Always follow the conventions in `.specify/specs/system/09-conventions.md`.
- Never introduce a dependency not already present in a module's `pom.xml` without explicit user approval.
- New classes must follow the naming and packaging conventions in `.specify/specs/system/09-conventions.md`.
- All new code must have tests as described in `.specify/specs/system/10-testing.md`.
- If a feature requires a change to the API model, update `.specify/specs/system/03-domain-model.md` after implementing it.
- If a feature adds or changes an API, update `.specify/specs/system/04-lifecycle.md` after implementing it.
- Build the project with `./mvnw verify` after every significant change and fix all errors before declaring the task complete.
- Every Java file MUST begin with the Apache 2.0 license header from `assets/license-header.txt`.
- Code is auto-formatted by Spotless (Palantir Java Format) during `./mvnw compile`. Never manually adjust formatting.

## Module Boundaries

- **paramixel-api**: Public annotations + context interfaces only. No engine, plugin, or persistence code. No dependency on `paramixel-engine`.
- **paramixel-engine**: Engine implementation only. No Maven plugin APIs. No `@Paramixel.TestClass`-annotated test classes in production source. Must not reference `paramixel-maven-plugin`.
- **paramixel-maven-plugin**: Maven Mojo only. No engine-internal package imports. No test author code.
- **paramixel-tests**: Functional tests via `@Paramixel.TestClass`. No JUnit Jupiter `@Test`. No Testcontainers. Imports only `paramixel-api` and `paramixel-engine`.
- **paramixel-examples**: Demonstrative tests. No engine unit tests. All Testcontainers dependencies are `test` scope only.
- **paramixel-benchmarks**: JMH benchmarks only. Must not run in standard builds or CI. Requires `-Pbenchmarks` profile.

## How to Navigate This Codebase

- Source root per module: `<module>/src/main/java/`
- Tests per module: `<module>/src/test/java/`
- Public API (interfaces + annotations): `api/src/main/java/org/paramixel/api/`
- Engine implementation: `engine/src/main/java/org/paramixel/engine/`
- Maven plugin: `maven-plugin/src/main/java/org/paramixel/maven/plugin/`
- Functional tests: `tests/src/test/java/test/`
- Examples: `examples/src/test/java/examples/`
- Engine SPI registration: `engine/src/main/resources/META-INF/services/org.junit.platform.engine.TestEngine`
- Application entry point: `org.paramixel.engine.ParamixelTestEngine` (JUnit Platform SPI)
- Maven plugin entry point: `org.paramixel.maven.plugin.ParamixelMojo` (goal: `test`)

## Build Commands

| Intent | Command |
|---|---|
| Full build + all tests | `./mvnw verify` |
| Compile only (also applies Spotless) | `./mvnw compile` |
| Single module tests (unit) | `./mvnw test -pl <module>` |
| Functional tests (tests module) | `./mvnw test -pl tests` |
| Functional tests (examples module) | `./mvnw test -pl examples` |
| Skip all tests | `./mvnw package -DskipTests -Dparamixel.skipTests=true` |
| Run a single unit test class | `./mvnw test -pl engine -Dtest=MyTest` |
| Run a single unit test method | `./mvnw test -pl engine -Dtest=MyTest#myMethod` |
| Apply code formatting only | `./mvnw spotless:apply` |
| Code coverage report (engine) | `./mvnw verify -pl engine` → `engine/target/site/jacoco/index.html` |

## Key Design Decisions to Respect

1. **Virtual threads**: All parallel execution uses `Executors.newVirtualThreadPerTaskExecutor()`. Do not introduce platform thread pools.
2. **Fair semaphores for concurrency**: `ParamixelConcurrencyLimiter` uses three fair `Semaphore` instances (total=`cores*2`, class=`cores`, argument=`cores`). Do not bypass this.
3. **Arguments collector invoked at discovery time**: `@Paramixel.ArgumentsCollector` is called during `discover()`, not `execute()`. This is intentional.
4. **Context hierarchy**: `EngineContext → ClassContext → ArgumentContext`. Each level has its own `Store`. Do not mix them.
5. **No IoC container**: All dependencies are wired manually via constructors. Never use `@Autowired`, `@Inject`, or `@Component`.
6. **Lifecycle exception handling**: "after" hooks (`@Paramixel.AfterEach`, `@Paramixel.AfterAll`, `@Paramixel.Finalize`) MUST run even after failures. "before" hooks (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, `@Paramixel.BeforeEach`) abort on first failure.
7. **InvocationTargetException unwrapping**: All reflection invocations go through `ParamixelReflectionInvoker.invokeMethod()` which unwraps `InvocationTargetException`.

## What OpenCode Should NOT Do

- Do not refactor code outside the scope of the requested task.
- Do not rename existing public APIs (`org.paramixel.api.*`) without explicit instruction.
- Do not change the engine ID (`"paramixel"`) — it is a stable SPI identifier.
- Do not add `@SuppressWarnings` to hide compiler or static analysis warnings.
- Do not use field injection (`@Autowired` on fields) — constructor injection only.
- Do not commit or push changes; only make edits.
- Do not add `System.out.println` to engine or API production code outside `ParamixelEngineExecutionListener`.
- Do not reference `org.paramixel.engine.*` internal packages from `paramixel-api`.
- Do not use JUnit Jupiter annotations in `@Paramixel.TestClass`-annotated test classes.
