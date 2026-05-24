---
title: Maven Plugin
description: Run Paramixel tests during the Maven test phase.
---

# Maven Plugin

The Paramixel Maven plugin discovers and executes `@Paramixel.Factory` methods during the `test` phase.

## Setup

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.paramixel</groupId>
            <artifactId>maven-plugin</artifactId>
            <version>5.0.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>test</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Plugin parameters

| Parameter | Property | Default | Description |
| --- | --- | --- | --- |
| `skipTests` | `paramixel.skipTests` | `false` | Skip Paramixel test execution |
| `failIfNoTests` | `paramixel.failIfNoTests` | `false` | Fail when no action factories are discovered |
| `failureOnSkip` | `paramixel.failureOnSkip` | `false` | Treat `SKIPPED` results as failures |
| `failureOnAbort` | `paramixel.failureOnAbort` | `true` | Treat `ABORTED` results as failures |
| `reportFile` | `paramixel.report.file` | (none) | Path to the per-run summary report |
| `matchPackage` | `paramixel.match.package.regex` | (none) | Regex for filtering discovered action factories by package name |
| `matchClass` | `paramixel.match.class.regex` | (none) | Regex for filtering discovered action factories by fully qualified class name |
| `matchTag` | `paramixel.match.tag.regex` | (none) | Regex for filtering discovered action factories by `@Paramixel.Tag` value |
| `properties` | — | — | Additional Paramixel configuration properties |
| `strictThreadLifecycle` | `paramixel.strictThreadLifecycle` | `false` | Fail when non-daemon threads linger after execution whose context classloader is a `URLClassLoader` |

## Configuration precedence

1. Test-classpath `paramixel.properties` and built-in defaults (lowest)
2. Plugin `<properties>` in the POM
3. Plugin `<reportFile>`, `<failureOnSkip>`, `<failureOnAbort>`, `<failIfNoTests>` parameters
4. `-Dparamixel.*` system properties (highest)

System properties always win over POM properties and plugin parameters when both set the same key.

## Custom properties

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

## CLI overrides

```bash
# Skip tests
./mvnw test -Dparamixel.skipTests=true

# Fail when no tests found
./mvnw test -Dparamixel.failIfNoTests=true

# Generate a report
./mvnw test -Dparamixel.report.file=target/paramixel/report.json

# Filter by tag
./mvnw test -Dparamixel.match.tag.regex=smoke

# Set parallelism
./mvnw test -Dparamixel.parallelism=8
```

## How it works

1. Builds a `URLClassLoader` from the project's test classpath
2. Sets the thread context classloader to the test classloader
3. Resolves action factories using the runner's internal classpath scanner
4. Executes discovered actions with `Runner`
5. Restores the original classloader
6. Checks for lingering non-daemon threads whose context classloader is a `URLClassLoader` (warns or fails depending on `strictThreadLifecycle`)

## Build failure conditions

The Maven build fails when the root action result is:

- `FAILED`
- `PENDING`
- `SKIPPED` (when `failureOnSkip=true`)
- `ABORTED` (when `failureOnAbort=true`, which is the default)

## Skipping JUnit tests

The Paramixel Maven plugin is independent of Surefire. Use `-DskipTests` to skip Surefire JUnit tests and `-Dparamixel.skipTests` to skip Paramixel tests. They are independent flags.

To skip all tests:

```bash
./mvnw test -DskipTests -Dparamixel.skipTests
```

## Next steps

- [Configuration Properties](../configuration/properties)
- [Gradle Integration](./gradle)
- [Reporting](./reporting)
