---
title: Configuration
description: Paramixel configuration sources and built-in keys.
---

# Configuration

## Core configuration sources

`org.paramixel.core.Configuration` exposes these entry points:

```java
Map<String, String> classpath = Configuration.classpathProperties();
Map<String, String> classpathWithLoader = Configuration.classpathProperties(classLoader);
Map<String, String> system = Configuration.systemProperties();
Map<String, String> defaults = Configuration.defaultProperties();
Map<String, String> defaultsWithLoader = Configuration.defaultProperties(classLoader);
```

All three methods return unmodifiable maps. Attempts to modify the returned map (such as `put`, `remove`, or `clear`) throw `UnsupportedOperationException`.

### Class-loader resolution

`Configuration` loads `paramixel.properties` using the thread context class loader first. If the context class loader is unavailable (`null`) or cannot find the resource, it falls back to the defining class loader (`Configuration.class.getClassLoader()`). This ensures configuration is reliably located in containers, plugins, and test runners that set a restricted context class loader.

## Built-in keys

### `paramixel.parallelism`

Controls default scheduler parallelism. This key is used by both `Runner` (for default worker concurrency) and `Resolver` (for the parallelism of discovered `Parallel` roots).

```properties
paramixel.parallelism=8
```

Notes:

- `Configuration.defaultProperties()` sets it to `Runtime.getRuntime().availableProcessors()` when absent.
- `Runner` uses this value to configure default scheduler worker concurrency.
- `Resolver` uses this value as the parallelism for discovered `Parallel` roots.
- Individual `Parallel` actions can also set an explicit per-node limit with `Parallel.builder(name).parallelism(...)`.

### `paramixel.failureOnSkip`

Controls whether SKIP results are treated as failures for exit code purposes.

```properties
paramixel.failureOnSkip=true
```

Notes:

- Default is `false`. When `false`, SKIP produces exit code `0` (same as PASS). When `true`, SKIP produces exit code `1` (same as FAIL).
- This affects `Runner.runAndReturnExitCode(Action)`, `Runner.runAndReturnExitCode(Selector)`, the Maven plugin, and `Runner.main()`.
- In the Maven plugin, this corresponds to the `<failureOnSkip>` POM parameter or `-Dparamixel.failureOnSkip=true` system property.
- With `Runner.main()`, set `-Dparamixel.failureOnSkip=true`.

### `paramixel.report.file`

Controls the file used for the summary report. When absent or blank, no report file is written.

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

Notes:

- The configured file is overwritten on each run.
- Parent directories are created on demand.
- Console output remains unchanged.
- The file contains the summary tree and footer only. It does not include per-action status lines or stack traces.
- If the report file cannot be created, Paramixel prints a warning to `System.err` and continues the run.
- Tilde (`~`) expansion is supported on Linux and macOS: `~` expands to the current user's home directory, `~/path` expands relative to the home directory, and `~user` expands to another user's home directory (if that user exists). On Windows, tilde expansion is a no-op and the path is used as-is.

Report format is inferred from the file extension:

- `.log` and `.txt` infer `text`
- `.json` infers `json`
- `.xml` infers `xml`
- `.html` infers `html`
- unknown or missing extensions default to `text`

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

### `paramixel.failIfNoTests`

Controls whether the absence of discovered action factories should produce a failing exit code.

```properties
paramixel.failIfNoTests=true
```

Notes:

- Default is `false`. When `false`, `Runner.main()` prints a message and exits with code `0` when no action factories are discovered. When `true`, it exits with code `1`.
- This is read by `Runner.main()`. In the Maven plugin, the same behavior is controlled by the `<failIfNoTests>` POM parameter.

## Accessing configuration inside actions

Prefer `Context#getConfiguration()`.

```java
private static Action printConfig() {
    return Direct.builder("print config")
            .runnable(context -> {
                String value = context.getConfiguration().get("my.custom.property");
            })
            .build();
}
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

Effective precedence in the plugin is:

1. test-classpath `paramixel.properties` and built-in defaults
2. plugin `<properties>`
3. `-Dparamixel.*` system properties

System properties win over POM `<properties>` when both set the same key.

The plugin passes configuration to discovery so that `paramixel.parallelism` controls both default scheduler worker concurrency and discovered action parallelism.

Example:

```xml
<configuration>
    <reportFile>${project.build.directory}/paramixel/paramixel.json</reportFile>
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
./mvnw test -Dparamixel.failIfNoTests=true
./mvnw test -Dparamixel.report.file=target/paramixel/paramixel.json
```

`paramixel.skipTests` and `paramixel.failIfNoTests` are Maven plugin parameters. `paramixel.failIfNoTests` is also a core `Configuration` key used by `Runner.main()`.

## Gradle configuration

When using `Runner.main()` with a Gradle `JavaExec` task, configuration is set via JVM system properties.

Precedence:

1. classpath `paramixel.properties` plus built-in defaults
2. JVM system properties (`-Dparamixel.*`)

Example `JavaExec` task:

```groovy
tasks.register('paramixelTest', JavaExec) {
    dependsOn 'testClasses'
    mainClass = 'org.paramixel.core.Runner'
    classpath = sourceSets.test.runtimeClasspath
    systemProperty('paramixel.parallelism', '4')
    systemProperty('paramixel.match.tag', 'smoke')
    systemProperty('paramixel.report.file', layout.buildDirectory.file('paramixel/paramixel.json').get().asFile.absolutePath)
}
```

CLI:

```bash
./gradlew paramixelTest -Dparamixel.parallelism=8
./gradlew paramixelTest -Dparamixel.match.tag=smoke
./gradlew paramixelTest -Dparamixel.report.file=build/paramixel/paramixel.json
```

See [Gradle](usage/gradle.md) for the full system property reference.
