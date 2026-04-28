---
id: quick-start
title: Quick Start
description: Get started with Paramixel in 5 minutes
---

# Quick Start

This guide will help you get started with Paramixel in just a few minutes.

## Prerequisites

- **Java 17 or higher**
- **Maven 3.9+**

## Add Dependency

Add Paramixel to your `pom.xml`:

```xml
<properties>
    <paramixel.version>YOUR_PARAMIXEL_VERSION</paramixel.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.paramixel</groupId>
        <artifactId>core</artifactId>
        <version>${paramixel.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.paramixel</groupId>
            <artifactId>maven-plugin</artifactId>
            <version>${paramixel.version}</version>
            <executions>
                <execution>
                    <phase>test</phase>
                    <goals>
                        <goal>test</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

The Maven plugin discovers `@Paramixel.ActionFactory` methods and executes the returned action trees during the `test` phase.

Use the latest published Paramixel release for `YOUR_PARAMIXEL_VERSION`.

## Write Your First Test

Create a test class with an `@Paramixel.ActionFactory` method:

```java
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;

public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Sequential.of("MyTest",
            Action.of("first test", Direct.of("first test",
                context -> {
                })),
            Action.of("second test", Direct.of("second test",
                context -> {
                })));
    }
}
```

### Key Concepts

- **`@Paramixel.ActionFactory`** — Marks a public static method that returns an `Action` tree
- **`Sequential.of()`** — Creates a composite action that runs children in order
- **`Action.of()`** — Wraps an action with a descriptive name
- **`Direct.of()`** — Creates a leaf action that executes an `Executable` callback
- **`Context`** — Provides access to `attachments`, `child execution`, and `listener notifications` during execution

## Run Tests

```bash
./mvnw test
```

The Maven plugin discovers all `@Paramixel.ActionFactory` methods, executes the action trees, and prints a summary table:

```
+---------------------------------------------+-----+------+-----+-----+-----+------+-----------+
| Paramixel Test Summary                                                                  |
+---------------------------------------------+-----+------+-----+-----+-----+------+-----------+
| Class                                        | Args|Methods|Passed|Failed|Skipped|Status| Time      |
+---------------------------------------------+-----+------+-----+-----+-----+------+-----------+
| MyTest                                       |   0 |    2 |   2 |   0 |   0 | PASS | 0.015 s   |
+---------------------------------------------+-----+------+-----+-----+-----+------+-----------+
| TOTAL                                        |   0 |    2 |   2 |   0 |   0 | PASS |           |
+---------------------------------------------+-----+------+-----+-----+-----+------+-----------+

Status         : TESTS PASSED
Execution Time : 0.015 s
```

## Next Steps

- [Action Composition](usage/action-composition) - Learn how to compose action trees with `Sequential`, `Parallel`, and `Lifecycle`
- [Lifecycle](actions/lifecycle) - Setup and teardown with guaranteed cleanup
- [Context](usage/context) - Attachments for sharing state
- [Maven Plugin](usage/maven-plugin) - Configure the Maven plugin and output formats
- [Configuration](configuration) - All configuration options

## Build from Source

To build Paramixel from source:

```bash
git clone https://github.com/paramixel/paramixel.git
cd paramixel
./mvnw clean install
```

## Verify Installation

Verify Paramixel is installed correctly by running the test suite:

```bash
./mvnw clean verify
```

Expected output (on supported JDKs): `BUILD SUCCESS`
