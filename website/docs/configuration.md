---
title: Configuration
description: Paramixel configuration sources and built-in keys.
---

# Configuration

## Core configuration sources

`org.paramixel.core.Configuration` exposes three entry points:

```java
Map<String, String> classpath = Configuration.classpathProperties();
Map<String, String> system = Configuration.systemProperties();
Map<String, String> defaults = Configuration.defaultProperties();
```

### `Configuration.defaultProperties()` precedence

1. `paramixel.properties` from the classpath root
2. JVM system properties
3. built-in defaults

Built-in defaults currently add `paramixel.parallelism` when absent.

## Built-in key

### `paramixel.parallelism`

Controls default runner parallelism.

```properties
paramixel.parallelism=8
```

Notes:

- `Configuration.defaultProperties()` sets it to `Runtime.getRuntime().availableProcessors()` when absent.
- `Runner` and the Maven plugin only fall back to `availableProcessors() * 2` if that key is somehow missing from the configuration they use.
- Individual `Parallel` actions can also set an explicit per-node limit with `Parallel.of(name, parallelism, ...)`.

## Accessing configuration inside actions

Prefer `Context#getConfiguration()`.

```java
Direct.of("print config", context -> {
    String value = context.getConfiguration().get("my.custom.property");
});
```

## `paramixel.properties`

Place the file on the runtime classpath, for example:

- `src/test/resources/paramixel.properties` for ordinary tests
- `src/main/resources/paramixel.properties` when examples live under `src/main/java`

Example:

```properties
paramixel.parallelism=4
my.custom.property=hello
```

## System properties

System properties override file values.

```bash
./mvnw test -Dparamixel.parallelism=16
```

## Programmatic runner configuration

`Runner.Builder#configuration(Map<String, String>)` overrides values from `Configuration.defaultProperties()`.

```java
Runner runner = Runner.builder()
        .configuration(Map.of("paramixel.parallelism", "6"))
        .build();
```

## Maven plugin configuration

The plugin accepts `<properties>` entries and also reads system properties.

Precedence in the plugin is:

1. `Configuration.defaultProperties()`
2. plugin `<properties>`
3. system properties whose keys start with `paramixel.`

Example:

```xml
<configuration>
    <properties>
        <property>
            <key>paramixel.parallelism</key>
            <value>4</value>
        </property>
    </properties>
</configuration>
```

Plugin flags:

```bash
./mvnw test -Dparamixel.skipTests=true
./mvnw test -Dparamixel.failIfNoTests=false
```

`paramixel.skipTests` and `paramixel.failIfNoTests` are Maven plugin parameters, not core `Configuration` keys.
