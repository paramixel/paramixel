---
title: Gradle Plugin
description: Run Paramixel tests with Gradle.
---

# Gradle Plugin

The Paramixel Gradle plugin provides the `paramixelTest` task for discovering and executing Paramixel action trees.

## Applying the plugin

In your `build.gradle.kts`:

```kotlin
plugins {
    id("org.paramixel") version "<PARAMIXEL_VERSION>"
}
```

Add `core` to the project's test classpath so test sources can compile against the Paramixel API:

```kotlin
dependencies {
    testImplementation("org.paramixel:core:<PARAMIXEL_VERSION>")
}
```

The plugin applies the `java` plugin, creates the `paramixel` DSL extension, and registers the `paramixelTest` task. The task is wired into the `check` lifecycle — running `./gradlew check` also runs Paramixel tests.

## DSL extension

Configure defaults via the `paramixel` extension:

```kotlin
paramixel {
    failIfNoTests.set(true)
    failureOnSkip.set(false)
    parallelism.set(4)
    matchTag.set("smoke")
    reportFile.set(layout.buildDirectory.file("paramixel/paramixel.json").map { it.asFile.absolutePath })
}
```

### Extension properties

| Property | Type | Default | Description |
|---|---|---|---|
| `skipTests` | `Property<Boolean>` | `false` | Skip Paramixel test run entirely |
| `failIfNoTests` | `Property<Boolean>` | `false` | Fail the build when no action factories are discovered |
| `failureOnSkip` | `Property<Boolean>` | `false` | Treat skipped results as failures |
| `parallelism` | `Property<Integer>` | unset | Runner parallelism level; framework default used when absent |
| `matchPackage` | `Property<String>` | unset | Regex filter for package names |
| `matchClass` | `Property<String>` | unset | Regex filter for fully-qualified class names |
| `matchTag` | `Property<String>` | unset | Regex filter for tags |
| `reportFile` | `Property<String>` | unset | File path for summary report; no report written when absent |

Unset optional properties do not overlay framework defaults — they are simply omitted from the configuration map.

## Task properties

The `paramixelTest` task inherits defaults from the extension and allows per-task overrides:

```kotlin
tasks.named<ParamixelTestTask>("paramixelTest") {
    parallelism.set(8)
    reportFile.set(layout.buildDirectory.file("paramixel/custom-report.html").map { it.asFile.absolutePath })
}
```

All extension properties are available as task inputs. Task-level values override extension defaults.

## CLI flags

```bash
./gradlew paramixelTest
./gradlew paramixelTest -Pparamixel.skipTests=true
./gradlew paramixelTest -Pparamixel.failIfNoTests=false
./gradlew paramixelTest -Pparamixel.failureOnSkip=true
./gradlew paramixelTest -Pparamixel.parallelism=8
./gradlew paramixelTest -Pparamixel.match.tag=smoke
./gradlew paramixelTest -Pparamixel.report.file=build/paramixel/paramixel.json
```

## Report files

When `reportFile` is set:

- the plugin creates parent directories on demand
- console output still appears normally
- the configured file is overwritten on each run
- the file contains the summary tree without ANSI color codes
- supported formats: `text`, `json`, `xml`, `html`
- format is inferred from the `reportFile` extension
- tilde (`~`) expansion is supported on Linux and macOS

Example output paths:

```text
build/paramixel/paramixel.json
~/reports/paramixel.html
```

## Configuration precedence

The plugin builds configuration in this order:

1. classpath `paramixel.properties` plus built-in defaults
2. DSL extension / task properties (only when `Property.isPresent()` is true)
3. explicit Gradle provider properties (`-Dparamixel.*` over `-Pparamixel.*`)

## Discovery behavior

The task builds a test classpath from `testSourceSet.runtimeClasspath` and calls:

```java
Resolver.resolveActions(configuration)
```

Filtering is supplied through configuration keys such as `paramixel.match.package`, `paramixel.match.class`, and `paramixel.match.tag`. Discovered factories are always combined as a `Parallel` root.

## Differences from the Maven plugin

| Feature | Gradle | Maven |
|---|---|---|
| `parallelism` DSL | First-class property | `<properties>` list |
| `matchPackage` / `matchClass` / `matchTag` | First-class properties | `<properties>` list or system properties |
| Arbitrary configuration keys | Not supported (use DSL properties) | Supported via `<properties>` list |
| Task goal | `paramixelTest` | `test` |
