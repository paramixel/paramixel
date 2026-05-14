---
title: Gradle
description: Run Paramixel tests with Gradle using Runner and JavaExec.
---

# Gradle

`Runner.main()` in the `core` module discovers and executes `@Paramixel.ActionFactory` methods from the classpath. Use it with a Gradle `JavaExec` task to run Paramixel tests.

Configuration is set via JVM system properties (`-Dparamixel.*`), which are read automatically by `Configuration.defaultProperties()`.

## build.gradle

```groovy
plugins {
    id 'java'
}

sourceSets {
    paramixel {
        java {
            srcDir 'core/src/main/java'
            srcDir 'examples/src/main/java'
        }
        resources {
            srcDir 'core/src/main/resources'
            srcDir 'examples/src/main/resources'
        }
    }
}

dependencies {
    paramixelImplementation 'io.github.classgraph:classgraph:4.8.184'
    paramixelImplementation 'org.apache.commons:commons-compress:1.28.0'
    paramixelImplementation 'org.apache.kafka:kafka-clients:4.2.0'
    paramixelImplementation 'org.assertj:assertj-core:3.27.7'
    paramixelImplementation 'org.junit.jupiter:junit-jupiter:6.0.3'
    paramixelImplementation 'org.mongodb:mongodb-driver-sync:5.7.0'
    paramixelImplementation 'org.slf4j:slf4j-nop:2.0.18'
    paramixelImplementation 'org.testcontainers:kafka:1.21.4'
    paramixelImplementation 'org.testcontainers:mongodb:1.21.4'
    paramixelImplementation 'org.testcontainers:nginx:1.21.4'
    paramixelImplementation 'org.testcontainers:testcontainers:2.0.5'
}

tasks.register('paramixelTest', JavaExec) {
    dependsOn 'paramixelClasses'
    group = 'verification'
    description = 'Discovers and executes Paramixel action trees'
    mainClass = 'org.paramixel.core.Runner'
    classpath = sourceSets.paramixel.runtimeClasspath
}

tasks.named('build').configure {
    dependsOn 'paramixelClasses'
}

tasks.named('test').configure {
    dependsOn 'paramixelTest'
}

tasks.named('check').configure {
    dependsOn 'paramixelTest'
}

tasks.named('build').configure {
    dependsOn 'paramixelClasses'
}

tasks.named('test').configure {
    dependsOn 'paramixelTest'
}

tasks.named('check').configure {
    dependsOn 'paramixelTest'
}
```

The `paramixel` sourceSet compiles both `core` and `examples` from source. No Maven local installation or published artifact is required. ClassGraph is the only runtime dependency for core; the remaining dependencies are required by the examples.

Running:

```bash
./gradlew build          # compile all sources
./gradlew test           # run Paramixel tests
./gradlew check          # same as test (includes paramixelTest)
./gradlew paramixelTest   # run Paramixel tests directly
```

## settings.gradle

```groovy
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'paramixel'
```

## gradle.properties

Set default Paramixel configuration in `gradle.properties`:

```properties
paramixel.failIfNoTests=true
paramixel.parallelism=4
```

JVM system properties are read by `Configuration.defaultProperties()` automatically.

## Configuration

Set Paramixel configuration via `systemProperty()` in the `JavaExec` task:

```groovy
tasks.register('paramixelTest', JavaExec) {
    dependsOn 'paramixelClasses'
    mainClass = 'org.paramixel.core.Runner'
    classpath = sourceSets.paramixel.runtimeClasspath
    systemProperty('paramixel.failIfNoTests', 'true')
    systemProperty('paramixel.parallelism', '8')
}
```

Or pass `-D` flags on the command line:

```bash
./gradlew paramixelTest -Dparamixel.failIfNoTests=true
./gradlew paramixelTest -Dparamixel.parallelism=8
./gradlew paramixelTest -Dparamixel.match.tag=smoke
./gradlew paramixelTest -Dparamixel.report.file=build/paramixel/paramixel.json
```

### Available system properties

| Property | Description |
|---|---|
| `paramixel.failIfNoTests` | Fail when no action factories are discovered (default: `false`) |
| `paramixel.failureOnSkip` | Treat skipped results as failures (default: `false`) |
| `paramixel.parallelism` | Runner parallelism level (default: available processors) |
| `paramixel.match.package` | Regex filter for package names |
| `paramixel.match.class` | Regex filter for fully qualified class names |
| `paramixel.match.tag` | Regex filter for `@Paramixel.Tag` values |
| `paramixel.report.file` | File path for summary report |

Match properties are optional. When omitted, all `@ActionFactory` methods are discovered. Only one location criterion is allowed: `paramixel.match.package` or `paramixel.match.class`, not both. `paramixel.match.tag` is orthogonal and can be combined with either location property.

## Multiple task configurations

Register separate tasks for different test subsets:

```groovy
tasks.register('paramixelSmokeTest', JavaExec) {
    dependsOn 'paramixelClasses'
    group = 'verification'
    description = 'Runs smoke-tagged Paramixel tests'
    mainClass = 'org.paramixel.core.Runner'
    classpath = sourceSets.paramixel.runtimeClasspath
    systemProperty('paramixel.match.tag', 'smoke')
    systemProperty('paramixel.report.file', layout.buildDirectory.file('paramixel/smoke.json').get().asFile.absolutePath)
}

tasks.register('paramixelIntegrationTest', JavaExec) {
    dependsOn 'paramixelClasses'
    group = 'verification'
    description = 'Runs integration Paramixel tests'
    mainClass = 'org.paramixel.core.Runner'
    classpath = sourceSets.paramixel.runtimeClasspath
    systemProperty('paramixel.match.package', 'com\\.example\\.integration')
    systemProperty('paramixel.parallelism', '2')
}
```

## Report files

When `paramixel.report.file` is set:

- console output still appears normally
- the configured file is overwritten on each run
- the file contains the summary tree without ANSI color codes
- supported formats: `text`, `json`, `xml`, `html`
- format is inferred from the file extension
- tilde (`~`) expansion is supported on Linux and macOS

Example output paths:

```text
build/paramixel/paramixel.json
~/reports/paramixel.html
```

## Configuration precedence

Configuration is resolved in this order:

1. classpath `paramixel.properties` plus built-in defaults
2. JVM system properties (`-Dparamixel.*`)

System properties override classpath defaults. See [Configuration](../configuration) for the full list of built-in keys.

## Differences from the Maven plugin

| Feature | Gradle | Maven |
|---|---|---|
| Task/goal | `paramixelTest` (user-defined `JavaExec`) | `test` (built-in mojo) |
| Configuration | `-Dparamixel.*` system properties | POM `<properties>`, `-D` system properties |
| Arbitrary config keys | Supported via `-D` system properties | Supported via `<properties>` list |
| Multiple configurations | Multiple `JavaExec` tasks | Separate executions |
| Plugin required | No (uses `core` + `JavaExec`) | Yes (`maven-plugin`) |
