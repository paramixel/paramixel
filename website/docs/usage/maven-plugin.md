---
title: Maven Plugin
description: Run Paramixel tests with Maven.
---

# Maven Plugin

The Paramixel Maven plugin provides the `test` goal.

## Parameters

### `skipTests`

- property: `paramixel.skipTests`
- default: `false`

### `failIfNoTests`

- property: `paramixel.failIfNoTests`
- default: `false`

### `failureOnSkip`

- property: `paramixel.failureOnSkip`
- default: `false`

When `true`, skipped tests cause the build to fail (equivalent to exit code `1`). When `false` (default), skipped tests are treated as successful (exit code `0`).

### `reportFile`

- property: `paramixel.report.file`
- default: unset

When set, the plugin writes a summary report to the configured file after execution completes.

### `reportFormat`

- property: `paramixel.report.format`
- default: inferred from `reportFile`

Supported values are `text`, `json`, `xml`, and `html`. When omitted, `.log` and `.txt` infer `text`, `.json` infers `json`, `.xml` infers `xml`, and `.html` infers `html`.

### `properties`

Custom key/value pairs merged into Paramixel runtime configuration.

## Example configuration

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>test</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <failIfNoTests>true</failIfNoTests>
        <reportFile>${project.build.directory}/paramixel/paramixel.json</reportFile>
        <properties>
            <property>
                <key>paramixel.parallelism</key>
                <value>4</value>
            </property>
        </properties>
    </configuration>
</plugin>
```

## CLI flags

```bash
./mvnw test -Dparamixel.skipTests=true
./mvnw test -Dparamixel.failIfNoTests=false
./mvnw test -Dparamixel.failureOnSkip=true
./mvnw test -Dparamixel.report.file=target/paramixel/paramixel.json
./mvnw test -Dparamixel.report.file=target/paramixel/report.out -Dparamixel.report.format=json
./mvnw test -Dparamixel.parallelism=8
```

## Report files

When report files are enabled:

- the plugin creates parent directories on demand
- console output still appears normally
- the configured file is overwritten on each run
- the file contains the summary tree without ANSI color codes
- file creation errors are reported as warnings and do not fail the test run
- tilde (`~`) expansion is supported on Linux and macOS: `~` expands to the current user's home directory, `~/path` expands relative to the home directory, and `~user` expands to another user's home directory (if that user exists). On Windows, tilde expansion is a no-op and the path is used as-is.

Example output paths:

```text
target/paramixel/paramixel.json
~/reports/paramixel.html
```

## Discovery behavior

The plugin builds a test classloader from:

- `target/test-classes` when present
- `target/classes` when present
- Maven test classpath dependencies

It then calls:

```java
Resolver.resolveActions(configuration, selector)
```

The configuration map is passed to the resolver so that `paramixel.parallelism` controls both thread pool sizing (in `Runner`) and discovered action parallelism (in `Resolver`).

Discovered factories are always combined as a `Parallel` root.

## Configuration precedence

The plugin builds configuration in this order:

1. `Configuration.defaultProperties()`
2. plugin `<properties>`
3. system properties whose keys start with `paramixel.`

## Source layout note

In this repository, Paramixel examples live under `examples/src/main/java` because the plugin discovers and runs action factories from compiled classes, not from JUnit's `src/test/java` convention.
