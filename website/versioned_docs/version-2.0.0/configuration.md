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

All three methods return unmodifiable maps. Attempts to modify the returned map (such as `put`, `remove`, or `clear`) throw `UnsupportedOperationException`.

### Class-loader resolution

`Configuration` loads `paramixel.properties` using the thread context class loader first. If the context class loader is unavailable (`null`) or cannot find the resource, it falls back to the defining class loader (`Configuration.class.getClassLoader()`). This ensures configuration is reliably located in containers, plugins, and test runners that set a restricted context class loader.

## Built-in keys

### `paramixel.parallelism`

Controls default runner parallelism. This key is used by both `Runner` (for thread pool sizing) and `Resolver` (for the parallelism of discovered `Parallel` roots).

```properties
paramixel.parallelism=8
```

Notes:

- `Configuration.defaultProperties()` sets it to `Runtime.getRuntime().availableProcessors()` when absent.
- `Runner` uses this value to size its thread pools and to detect potential nested-parallel deadlocks.
- `Resolver` uses this value as the parallelism for discovered `Parallel` roots.
- Individual `Parallel` actions can also set an explicit per-node limit with `Parallel.of(name, parallelism, ...)`.

### `paramixel.failureOnSkip`

Controls whether SKIP results are treated as failures for exit code purposes.

```properties
paramixel.failureOnSkip=true
```

Notes:

- Default is `false`. When `false`, SKIP produces exit code `0` (same as PASS). When `true`, SKIP produces exit code `1` (same as FAIL).
- This affects `Runner.runAndReturnExitCode(Action)`, `Runner.runAndReturnExitCode(Selector)`, and the Maven plugin.
- In the Maven plugin, this corresponds to the `<failureOnSkip>` POM parameter or `-Dparamixel.failureOnSkip=true` system property.

### `paramixel.match.package`

Regex pattern for filtering discovered action factories by package name.

```properties
paramixel.match.package=com\.example
```

When set, only action factories whose declaring class package matches this regex are included. Uses `Pattern.matcher().find()` (substring match). For exact match, anchor with `^...$`.

### `paramixel.match.class`

Regex pattern for filtering discovered action factories by fully qualified class name.

```properties
paramixel.match.class=com\.example\.MyTest
```

When set, only action factories whose declaring class name matches this regex are included. Uses `Pattern.matcher().find()` (substring match). For exact match, anchor with `^...$`.

### `paramixel.match.tag`

Regex pattern for filtering discovered action factories by `@Paramixel.Tag` value.

```properties
paramixel.match.tag=smoke
```

When set, only action factories annotated with a matching `@Paramixel.Tag` are included. Uses `Pattern.matcher().find()` (substring match). For exact match, anchor with `^...$`.

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

## Programmatic discovery configuration

`Resolver` overloads that accept a `Map<String, String> configuration` parameter control the parallelism of discovered `Parallel` roots. When `paramixel.parallelism` is present in the map, that value is used; otherwise `Configuration.defaultProperties()` supplies the default.

```java
Optional<Action> root = Resolver.resolveActions(
        Map.of(Configuration.RUNNER_PARALLELISM, "4"));
```

Overloads that do not accept a configuration map use `Configuration.defaultProperties()`.

## Maven plugin configuration

The plugin accepts `<properties>` entries and also reads system properties.

Precedence in the plugin is:

1. `Configuration.defaultProperties()`
2. plugin `<properties>`
3. system properties whose keys start with `paramixel.`

The plugin passes configuration to discovery so that `paramixel.parallelism` controls both thread pool sizing and discovered action parallelism.

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