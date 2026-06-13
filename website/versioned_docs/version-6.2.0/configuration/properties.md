---
title: Configuration Properties
description: Paramixel properties, defaults, and precedence.
---

# Configuration Properties

Paramixel configuration is properties-based. The classpath resource name is `paramixel.properties`.

## Precedence

For `Configuration.defaultConfiguration()`, values are layered in this order:

1. `paramixel.properties` from the classpath
2. JVM system properties
3. built-in defaults for unset framework keys

Later layers override earlier layers.

## Example

```properties
paramixel.parallelism=4
paramixel.failureOnAbort=true
paramixel.report.file=target/paramixel-report.html
paramixel.listener.exclude=quiet
```

## Programmatic configuration

```java
import java.util.Map;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;

var configuration = Configuration.of(Map.of(
        "paramixel.parallelism", "4",
        "paramixel.match.tag.regex", "critical"));

var runner = Runner.builder().configuration(configuration).build();
```

Use `paramixel.properties` for classpath defaults, JVM `-D` system properties for environment-specific overrides, or programmatic `Configuration` objects when constructing a `Runner`.

See [Configuration API](../api/configuration) for the complete list of supported keys and typed getter methods.

## Caching

`Configuration.defaultConfiguration()` is computed once per classloader and cached
indefinitely. If you set a JVM system property after the first call — for example,
`System.setProperty("paramixel.parallelism", "8")` at runtime — subsequent
`defaultConfiguration()` calls will continue to return the **original** snapshot.

Use `Configuration.systemConfiguration()` to get a snapshot reflecting all current
system properties, or construct a `Configuration` directly with `Configuration.of(...)`
for programmatic overrides.

See [Configuration API](../api/configuration#caching) for more details.
