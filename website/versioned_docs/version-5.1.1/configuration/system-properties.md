---
title: System Properties
description: Override Paramixel configuration with JVM system properties.
---

# System Properties

JVM system properties prefixed with `paramixel.` override classpath configuration and built-in defaults. They are the highest-priority configuration source after programmatic runner configuration.

## Setting system properties

### Maven

```bash
./mvnw test -Dparamixel.parallelism=8
./mvnw test -Dparamixel.match.tag.regex=smoke
./mvnw test -Dparamixel.report.file=target/paramixel/report.json
```

### Gradle

```bash
./gradlew paramixelTest -Dparamixel.parallelism=8
```

Or in the build file:

```groovy
tasks.register('paramixelTest', JavaExec) {
    systemProperty('paramixel.parallelism', '8')
    systemProperty('paramixel.match.tag.regex', 'smoke')
}
```

### Programmatic

```java
System.setProperty("paramixel.parallelism", "8");
```

## Boolean parsing

Boolean configuration values use strict parsing: only the trimmed, case-insensitive string `"true"` returns `true`. Every other value — including `null` — returns `false`.

```properties
paramixel.failureOnSkip=true    # → true
paramixel.failureOnSkip=True    # → true
paramixel.failureOnSkip=yes     # → false
paramixel.failureOnSkip=1       # → false
```

## Precedence

| Priority | Source | Example |
| --- | --- | --- |
| 1 (lowest) | Built-in defaults | `paramixel.parallelism=availableProcessors` |
| 2 | Classpath `paramixel.properties` | `paramixel.parallelism=4` |
| 3 | Maven POM `<properties>` (plugin only) | `<key>paramixel.parallelism</key>` |
| 4 | Maven `<reportFile>` parameter (plugin only) | `<reportFile>...</reportFile>` |
| 5 | JVM system properties `-Dparamixel.*` | `-Dparamixel.parallelism=8` |
| 6 (highest) | Programmatic `Runner.builder().configuration(...)` | `Map.of(...)` |

## Available system properties

See [Configuration Properties](./properties) for the full list of built-in keys. All keys prefixed with `paramixel.` are recognized as Paramixel system properties.

Custom properties can also be passed via system properties:

```bash
./mvnw test -Dparamixel.my.custom.key=value
```

These are accessible via `Configuration.systemConfiguration()` and `Context#configuration()`.

## Next steps

- [Configuration Properties](./properties)
- [Maven Plugin](../integrations/maven-plugin)
