# Paramixel Examples

Test classes and integration tests demonstrating the Paramixel framework, including Altcontainers-based tests against Kafka, MongoDB, and Nginx.

## Why `src/main/java/`?

Tests live under `src/main/java/` (not `src/test/java/`) because they are executed by the Paramixel Maven plugin during the `test` phase, not by JUnit directly. This is why JUnit Jupiter and AssertJ are declared with `compile` scope in the POM — they are runtime dependencies of the test classes, not test-scope dependencies.

## Package Structure

| Package | Purpose | Has Runner |
|---|---|---|
| `examples` | Parallel/sequential matrices, assertion and sleep steps, nested pools, lifecycle and retry examples | Yes |
| `examples.annotation` | `@Disabled` annotation behavior | Yes |
| `examples.annotation.tags` | Tag-based test selection | Yes |
| `examples.argument` | Argument type variants (primitives, collections, custom types, etc.) | Yes |
| `examples.lifecycle` | Custom listeners and full lifecycle | Yes |
| `examples.retry` | Bounded retry policies, fresh instance per attempt, attempt history | Yes |
| `examples.support` | Shared utilities (Logger, Resource, Debug) | N/A |
| `examples.isolated` | Exclusive lock serialization inside parallel work | Yes |
| `examples.loop` | Bounded `repeat` with `until`, `untilPassed`, and `Backoff` delays | Yes |
| `examples.fixtures` | The fixture-per-environment shape: `instance`, `each`, and a bounded pool | No |
| `examples.altcontainers.kafka` | Kafka integration test + environment + container spec | N/A |
| `examples.altcontainers.mongodb` | MongoDB integration test + environment + container spec | N/A |
| `examples.altcontainers.nginx` | Nginx integration test + environment + container spec | N/A |
| `examples.altcontainers.util` | Container log consumer, random utilities | N/A |

## `__ParamixelRunner__` Convention

Most top-level runnable example packages contain a `__ParamixelRunner__` class that serves as an entry point to run all tests in that package from the console or IDE. Nested container packages are run through `examples.altcontainers.__ParamixelRunner__`. The double-underscore naming (`__`) sorts the file to the top of the package in IDE file trees, making the runner immediately visible.

```java
public class __ParamixelRunner__ {
    public static void main(String[] args) {
        System.exit(Runner.builder()
                .build()
                .run(Selector.builder()
                        .packageTreeOf(__ParamixelRunner__.class)
                        .build())
                .exitCode());
    }
}
```

Key details:

- **Selection**: `Selector.packageTreeOf()` selects all classes in the specified package and its subpackages. For example, running `examples.annotation.__ParamixelRunner__` executes `DisabledTest` in `examples.annotation` as well as `CriticalTaggedTest` and `SmokeTaggedTest` in `examples.annotation.tags`.
- **PMD violations**: The `__ParamixelRunner__` name intentionally violates PMD's `ClassNamingConventions` rule. These are tooling entry points, not application classes.
- **`examples.support`** has no runner because it contains only utility classes, not test classes.

## Altcontainers Pattern

Each container type follows an `XxxTest.java` + `XxxTestEnvironment.java` + `XxxContainerSpec.java` trio:

- **`XxxTest.java`** — Paramixel test class containing the actual test logic
- **`XxxTestEnvironment.java`** — Manages the container lifecycle: network ownership, create/close, and readiness probing
- **`XxxContainerSpec.java`** — A `GenericContainerSpec` subclass pre-filled with the image configuration (ports, command, environment, wait conditions, timeouts)

Altcontainers ships no service modules, so all image configuration is stated explicitly in each spec.

Shared utilities live in `examples.altcontainers.util/`:

- `ContainerLogConsumer` — Streams container output to `System.out`
- `RandomUtil` — Generates random strings and numbers

### Docker Images

Container image versions are loaded from package-relative `docker-images.txt` classpath resources, such as `examples/altcontainers/kafka/docker-images.txt`. This allows image versions to be updated without modifying Java source.

### Container Cleanup on JVM Crash

Altcontainers' reaper provides automatic cleanup if the JVM crashes mid-test. The core module launches a separate
reaper process per JVM session; it holds a TCP connection to the application as a liveness signal and, when that
connection drops, destroys every container and network carrying the session's labels — including after `SIGKILL`,
OOM, and other hard crashes. The `setUp()`/`tearDown()` lifecycle in each `*TestEnvironment` handles normal
execution; the reaper is the safety net for abnormal exits.

Disabling the reaper (`-Daltcontainers.reaper.disabled=true`) removes this safety net and may leave orphan
containers on crash.

## Running Tests

```bash
# Run all examples tests (requires Docker)
./mvnw test -pl examples

# Build without running examples tests
./mvnw clean install -Dparamixel.skipTests

# Run a single package from an IDE
# Run __ParamixelRunner__.main() in the desired package
```
