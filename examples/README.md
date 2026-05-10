# Paramixel Examples

Test classes and integration tests demonstrating the Paramixel framework, including Testcontainers-based tests against Kafka, MongoDB, and Nginx.

## Why `src/main/java/`?

Tests live under `src/main/java/` (not `src/test/java/`) because they are executed by the Paramixel Maven plugin during the `test` phase, not by JUnit directly. This is why JUnit Jupiter and AssertJ are declared with `compile` scope in the POM — they are runtime dependencies of the test classes, not test-scope dependencies.

## Package Structure

| Package | Purpose | Has Runner |
|---|---|---|
| `examples` | Parallel/sequential argument tests | Yes |
| `examples.annotation` | `@Disabled` annotation behavior | Yes |
| `examples.annotation.tags` | Tag-based test selection | Yes |
| `examples.argument` | Argument type variants (primitives, collections, custom types, etc.) | Yes |
| `examples.context` | Context inheritance and sharing | Yes |
| `examples.lifecycle` | Custom listeners and full lifecycle | Yes |
| `examples.store` | Store context and operations | Yes |
| `examples.support` | Shared utilities (Logger, Resource, NetworkFactory, Debug) | N/A |
| `examples.testcontainers` | Testcontainers integration tests | Yes |
| `examples.testcontainers.kafka` | Kafka integration test + environment + wait strategy | N/A |
| `examples.testcontainers.mongodb` | MongoDB integration test + environment | N/A |
| `examples.testcontainers.nginx` | Nginx integration test + environment | N/A |
| `examples.testcontainers.util` | Container log consumer, random utilities | N/A |

## `__ParamixelRunner__` Convention

Each test package contains a `__ParamixelRunner__` class that serves as an entry point to run all tests in that package from the console or IDE. The double-underscore naming (`__`) sorts the file to the top of the package in IDE file trees, making the runner immediately visible.

```java
public class __ParamixelRunner__ {
    public static void main(String[] args) {
        Factory.defaultRunner()
                .runAndExit(Selector.builder()
                        .packageOf(__ParamixelRunner__.class)
                        .build());
    }
}
```

Key details:

- **Scope**: `Selector.packageOf()` selects all classes in the specified package and its subpackages. For example, running `examples.annotation.__ParamixelRunner__` executes `DisabledTest` in `examples.annotation` as well as `CriticalTaggedTest` and `SmokeTaggedTest` in `examples.annotation.tags`.
- **PMD violations**: The `__ParamixelRunner__` name intentionally violates PMD's `ClassNamingConventions` rule. These are tooling entry points, not application classes.
- **`examples.support`** has no runner because it contains only utility classes, not test classes.

## Testcontainers Pattern

Each container type follows a `XxxTest.java` + `XxxTestEnvironment.java` pair:

- **`XxxTest.java`** — Paramixel test class containing the actual test logic
- **`XxxTestEnvironment.java`** — Manages the Testcontainers lifecycle (create, start, stop, configure)

Shared utilities live in `examples.testcontainers.util/`:

- `ContainerLogConsumer` — Streams container stdout to `System.out`
- `RandomUtil` — Generates random strings and numbers

### Docker Images

Container image versions are loaded from `/docker-images.txt` classpath resources (one per test category). This allows image versions to be updated without modifying Java source.

### Container Cleanup on JVM Crash

Testcontainers' Ryuk container provides automatic cleanup if the JVM crashes mid-test. Ryuk tracks all containers by session ID and removes them when the JVM process exits — including on SIGKILL, OOM, and other hard crashes. The `setUp()`/`tearDown()` lifecycle in each `*TestEnvironment` handles normal execution; Ryuk is the safety net for abnormal exits.

Disabling Ryuk (`TESTCONTAINERS_RYUK_DISABLED=true`) removes this safety net and may leave orphan containers on crash.

## Running Tests

```bash
# Run all examples tests (requires Docker)
./mvnw test -pl examples

# Build without running examples tests
./mvnw clean install -Dparamixel.skipTests

# Run a single package from an IDE
# Run __ParamixelRunner__.main() in the desired package
```
