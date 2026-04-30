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
./mvnw test -Dparamixel.parallelism=8
```

## Discovery behavior

The plugin builds a test classloader from:

- `target/test-classes` when present
- `target/classes` when present
- Maven test classpath dependencies

It then calls:

```java
Resolver.resolveActions(testClassLoader)
```

That means discovered factories are combined with the resolver default, which is `Resolver.Composition.PARALLEL`.

## Configuration precedence

The plugin builds configuration in this order:

1. `Configuration.defaultProperties()`
2. plugin `<properties>`
3. JVM system properties whose keys start with `paramixel.`

## Source layout note

In this repository, Paramixel examples live under `examples/src/main/java` because the plugin discovers and runs action factories from compiled classes, not from JUnit's `src/test/java` convention.
