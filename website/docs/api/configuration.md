---
title: Configuration
description: Typed access to Paramixel configuration properties with layered resolution.
---

# Configuration

The `Configuration` interface provides typed access to Paramixel configuration properties. Configuration is resolved in layers: classpath properties, JVM system properties, and framework defaults.

```java
import org.paramixel.api.Configuration;
```

## Factory methods

| Method | Description |
| --- | --- |
| `Configuration.defaultConfiguration()` | Classpath + system properties + defaults |
| `Configuration.defaultConfiguration(ClassLoader)` | Same, with a preferred classloader |
| `Configuration.classpathConfiguration()` | Classpath properties only |
| `Configuration.classpathConfiguration(ClassLoader)` | Classpath properties with a preferred classloader |
| `Configuration.systemConfiguration()` | JVM system properties + defaults |
| `Configuration.of(Map<String, String>)` | From a supplied map; defensively copied |

## Typed getters

All typed getters return `Optional`. Absent keys produce empty optionals. Invalid values throw [`ConfigurationException`](./exception-reference).

| Method | Return | Description |
| --- | --- | --- |
| `getString(String key)` | `Optional<String>` | The raw string value |
| `getBoolean(String key)` | `Optional<Boolean>` | Parsed as a Paramixel boolean (only trimmed case-insensitive `"true"` is true) |
| `getInteger(String key)` | `Optional<Integer>` | Parsed as an integer |
| `getLong(String key)` | `Optional<Long>` | Parsed as a long |
| `getFloat(String key)` | `Optional<Float>` | Parsed as a float |
| `getDouble(String key)` | `Optional<Double>` | Parsed as a double |
| `get(String key, Function<String, T> transformer)` | `Optional<T>` | Transformed by a custom function |

## Other methods

| Method | Description |
| --- | --- |
| `keySet()` | All configuration keys present in this configuration |
| `parseBoolean(String value)` | Static; only trimmed case-insensitive `"true"` returns `true` |

## Configuration keys

| Constant | Key | Type | Default |
| --- | --- | --- | --- |
| `RUNNER_PARALLELISM` | `paramixel.parallelism` | int | Available processors |
| `SCHEDULER_QUEUE_CAPACITY` | `paramixel.scheduler.queue.capacity` | int | 1024 |
| `ANSI` | `paramixel.ansi` | string | `"auto"` |
| `FAILURE_ON_SKIP` | `paramixel.failureOnSkip` | boolean | `false` |
| `FAILURE_ON_ABORT` | `paramixel.failureOnAbort` | boolean | `true` |
| `FAIL_IF_NO_TESTS` | `paramixel.failIfNoTests` | boolean | `false` |
| `REPORT_FILE` | `paramixel.report.file` | string | (none) |
| `MATCH_PACKAGE_REGEX` | `paramixel.match.package.regex` | regex | (none) |
| `MATCH_CLASS_REGEX` | `paramixel.match.class.regex` | regex | (none) |
| `MATCH_TAG_REGEX` | `paramixel.match.tag.regex` | regex | (none) |
| `CONFIGURATION_FILE_NAME` | (constant) | string | `"paramixel.properties"` |

The classpath resource name searched for Paramixel configuration. When this resource is absent, classpath properties are empty.

## Programmatic configuration

```java
Configuration config = Configuration.of(Map.of(
    Configuration.RUNNER_PARALLELISM, "4",
    Configuration.REPORT_FILE, "target/reports/run.json"));

Runner runner = Runner.builder()
    .configuration(config)
    .build();
```

See [Properties](../configuration/properties) and [System Properties](../configuration/system-properties) for full configuration details.
