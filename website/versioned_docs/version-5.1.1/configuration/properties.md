---
title: Configuration Properties
description: Paramixel configuration from classpath properties, system properties, defaults, and explicit configuration.
---

# Configuration Properties

Paramixel exposes configuration through `org.paramixel.api.Configuration`. Values are read through typed getters such as `getString`, `getBoolean`, and `getInteger`.

## Configuration sources

Use one of the factory methods:

```java
Configuration configuration = Configuration.defaultConfiguration();
Configuration classpathOnly = Configuration.classpathConfiguration();
Configuration systemAndDefaults = Configuration.systemConfiguration();
Configuration explicit = Configuration.of(Map.of(
        Configuration.RUNNER_PARALLELISM, "4"));
```

`Configuration.defaultConfiguration()` resolves classpath `paramixel.properties`, JVM system properties, and built-in defaults. System properties override classpath properties; built-in defaults fill unset framework keys.

`Configuration.of(Map<String, String>)` creates an explicit configuration from the supplied map.

## Built-in keys

### `paramixel.parallelism`

Controls concurrency through three layers:

1. **Top-level Parallel throttle** — a semaphore initialized to this value gates how many direct children of the root `Parallel` action are in-flight simultaneously
2. **Per-Parallel admission** — each `Parallel` action's effective parallelism defaults to this value; explicitly configured `.parallelism(N)` is capped by it
3. **Scheduler thread pools and leaf permits** — one `ThreadPoolExecutor` per descriptor depth is created lazily when a `Parallel` schedules children at that depth (non-Parallel composites run children synchronously); each pool is sized to this value; a global leaf semaphore (also this value) limits concurrent leaf-action execution

Default is `Runtime.getRuntime().availableProcessors()`.

```properties
paramixel.parallelism=8
```

See [Parallel Execution](../guides/parallel-execution#how-paramixelparallelism-works) for the full three-layer concurrency model.

### `paramixel.scheduler.queue.capacity`

Maximum number of scheduler-ready tasks and permit waiters. Default is `1024`.

```properties
paramixel.scheduler.queue.capacity=2048
```

### `paramixel.ansi`

Controls ANSI escape code output in console listeners.

| Value | Behavior |
| --- | --- |
| `true` | Force ANSI on |
| `false` | Force ANSI off |
| `auto` (default) | Auto-detect based on terminal |

### `paramixel.failureOnSkip`

Whether skipped results produce a failing effective status/exit code. Default is `false`.

```properties
paramixel.failureOnSkip=true
```

### `paramixel.failureOnAbort`

Whether aborted results produce a failing effective status/exit code. Default is `true`.

```properties
paramixel.failureOnAbort=false
```

### `paramixel.failIfNoTests`

Whether absence of discovered action factories produces a failing effective status/exit code. Default is `false`.

```properties
paramixel.failIfNoTests=true
```

### `paramixel.listener.exclude`

Controls which listener output sections are suppressed. The value is a comma-separated list of tokens. When absent or blank, all sections are printed.

| Token | Effect |
| --- | --- |
| `status.header` | Exclude per-action "starting" header lines |
| `status.footer` | Exclude per-action completion status lines |
| `status` | Shorthand for `status.header,status.footer` |
| `summary.header` | Exclude the `"Paramixel vX starting..."` header |
| `summary.tree` | Exclude the rendered action tree |
| `summary.footer` | Exclude the status / timestamp / total time block |
| `quiet` | Shorthand for `status,summary.tree` |
| `all` | Exclude all sections |

Exception stack traces and the "No Paramixel tests found" message always print regardless of exclude tokens.

```properties
paramixel.listener.exclude=quiet
paramixel.listener.exclude=status,summary.tree
paramixel.listener.exclude=status.header
```

### `paramixel.report.file`

Path to the per-run summary report. When unset, no report file is generated.

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

Report format is inferred from the file extension: `.json`, `.xml`, `.html`, or plain text for `.log`, `.txt`, and other extensions.

### `paramixel.match.package.regex`

Regex for filtering discovered action factories by package name. Uses `find()` semantics.

```properties
paramixel.match.package.regex=com\.example\.tests
```

### `paramixel.match.class.regex`

Regex for filtering discovered action factories by fully qualified class name. Uses `find()` semantics.

```properties
paramixel.match.class.regex=^com\.example\.MyTest$
```

### `paramixel.match.tag.regex`

Regex for filtering discovered action factories by `@Paramixel.Tag` value. Uses `find()` semantics.

```properties
paramixel.match.tag.regex=smoke
```

### `paramixel.strictThreadLifecycle` (Maven plugin only)

This is a Maven plugin parameter, not a core `Configuration` constant. It is not recognized by programmatic `Configuration.of(...)`. When enabled, the Maven plugin fails the build when non-daemon threads linger after execution whose context classloader is a `URLClassLoader` (i.e., threads that retain a reference to the test classloader).

```properties
paramixel.strictThreadLifecycle=true
```

See [Maven Plugin](../integrations/maven-plugin) for details.

## `paramixel.properties`

Place `paramixel.properties` on the runtime classpath:

```properties
paramixel.parallelism=4
paramixel.report.file=target/paramixel/paramixel.json
paramixel.failureOnSkip=true
my.custom.property=hello
```

## System properties

System properties override classpath values:

```bash
./mvnw test -Dparamixel.parallelism=16
./gradlew paramixelTest -Dparamixel.parallelism=16
```

## Programmatic configuration

```java
Runner runner = Runner.builder()
        .configuration(Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM, "4",
                Configuration.REPORT_FILE, "build/paramixel/report.json")))
        .build();
```

## Accessing configuration inside actions

Use `Context#configuration()` in context-mode steps or custom actions:

```java
Step.of("print-config", context -> {
    context.configuration()
            .getString("my.custom.property")
            .ifPresent(System.out::println);
});
```

## Typed getters

All typed getters return `Optional` and throw `ConfigurationException` when a present value cannot be parsed as the requested type.

```java
int parallelism = configuration.getInteger(Configuration.RUNNER_PARALLELISM).orElse(1);
boolean failOnSkip = configuration.getBoolean(Configuration.FAILURE_ON_SKIP).orElse(false);
```

## Next steps

- [Maven Plugin](../integrations/maven-plugin)
- [Gradle Integration](../integrations/gradle)
