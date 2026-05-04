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
- default: `true`

### `failureOnSkip`

- property: `paramixel.failureOnSkip`
- default: `false`

When `true`, skipped tests cause the build to fail (equivalent to exit code `1`). When `false` (default), skipped tests are treated as successful (exit code `0`).

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
        <failIfNoTests>false</failIfNoTests>
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
./mvnw test -Dparamixel.parallelism=8
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