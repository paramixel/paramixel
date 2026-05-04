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

The same fallback strategy is used by `Information` to locate `information.properties`.

### `Configuration.defaultProperties()` precedence

1. `paramixel.properties` from the classpath root
2. JVM system properties
3. built-in defaults

Built-in defaults currently add `paramixel.parallelism` when absent.

## Built-in keys

### `paramixel.parallelism`

Controls default runner parallelism. This key is used by both `Runner` (for thread pool sizing) and `Resolver` (for the parallelism of discovered `Parallel` roots).

```properties
paramixel.parallelism=8
```

Notes:

- `Configuration.defaultProperties()` sets it to `Runtime.getRuntime().availableProcessors()` when absent.
- `Runner` uses this value to size its thread pools and to detect potential nested-parallel deadlocks.
- `Resolver` uses this value as the parallelism for discovered `Parallel` roots when using `Composition.PARALLEL`. Overloads that accept a `Map<String, String> configuration` parameter let you control discovery parallelism explicitly.
- Individual `Parallel` actions can also set an explicit per-node limit with `Parallel.of(name, parallelism, ...)`.

### `paramixel.failureOnSkip`

Controls whether SKIP results are treated as failures for exit code purposes.

```properties
paramixel.failureOnSkip=true
```

Notes:

- Default is `false`. When `false`, SKIP produces exit code `0` (same as PASS). When `true`, SKIP produces exit code `1` (same as FAIL).
- This affects `ConsoleRunner.runAndReturnExitCode(Action, Map)`, `ConsoleRunner.runAndReturnExitCode(Selector, Map)`, and the Maven plugin.
- The no-configuration overloads (`runAndReturnExitCode(Action)` and `runAndReturnExitCode(Selector)`) always treat SKIP as success (exit code `0`).
- In the Maven plugin, this corresponds to the `<failureOnSkip>` POM parameter or `-Dparamixel.failureOnSkip=true` system property.

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
        Selector.byPackageName(MyTest.class),
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