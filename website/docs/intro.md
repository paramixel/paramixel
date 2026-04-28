---
id: intro
title: Paramixel
description: An action-based test engine for Java 17+ with composable action trees, lifecycle management, and parallel execution.
slug: /
---

# Paramixel

An action-based test engine for Java 17+ with composable action trees, lifecycle management, and parallel execution.

## Why Paramixel?

Most Java test frameworks organize tests as a flat list of annotated methods. That works well for simple test suites — but as complexity grows, annotation-driven approaches break down:

- **Shared setup and teardown** — lifecycle annotations don't guarantee cleanup on failure or skip, and sharing state across tests requires workarounds
- **Dynamic test generation** — you can't loop over `@Test` annotations; parameterized tests require dedicated runners and limited flexibility
- **Mixed parallelism** — parallel execution is typically all-or-nothing at the class or method level, not composable within a single test plan
- **Test dependencies** — when one test's failure means the rest should be skipped, you're on your own

Paramixel takes a different approach: **tests are trees, not lists.**

Compose `Sequential`, `Parallel`, `Lifecycle`, `StrictSequential`, and `RandomSequential` actions to arbitrary depth, making test topology explicit and programmatic. Build test plans with loops, conditionals, and dynamic generation — plain Java code, no annotation tricks. Guaranteed teardown follows try-with-resources semantics — cleanup always runs, even on failure or skip, with errors attached as suppressed exceptions.

Paramixel runs anywhere Java runs. Use the Maven plugin for seamless CI/CD with `mvn test`, or embed the programmatic API directly:

```java
Result result = Runner.builder().build().run(actionFactory());
```

No build tool lock-in. No special CI configuration. Need more control? The `Runner` interface is yours to implement — customize execution semantics, plug in custom reporting, or integrate with external systems.

## Key Benefits

- **Composable action trees** — Build test hierarchies with `Sequential`, `StrictSequential`, `Parallel`, and `Lifecycle` actions
- **Programmatic test definition** — Build test plans with Java code (loops, conditionals, dynamic generation) instead of declarative annotations
- **Guaranteed teardown** — `Lifecycle` actions always run teardown, even on failure or skip
- **Parallel execution** — Configurable parallelism at any level of the action tree
- **Fail-fast or run-all semantics** — `StrictSequential` stops on first failure; `Sequential` runs all children regardless; choose the right behavior per group
- **Extensible by design** — Actions own their execution via `doExecute()` and use `Context.execute()`/`Context.executeAsync()` for children; write custom actions with zero executor changes
- **Context attachments** — Each context can attach one typed object for the action's lifetime
- **Maven plugin integration** — Table and tree summary output formats
- **Pluggable configuration** — Via `paramixel.properties`, system properties, or Maven plugin properties

## Quick Links

- [Quick Start](quick-start) - Get started in 5 minutes
- [Usage Guide](usage/action-composition) - Learn action composition and patterns
- [Configuration](configuration) - All configuration options
- [API Reference](api/intro) - Public API quick reference
- [Architecture](architecture) - Internal design

## Installation

### Maven

Add the Paramixel dependencies and Maven plugin to your `pom.xml`:

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

The Maven plugin discovers `@Paramixel.ActionFactory` methods on the test classpath and executes the returned action trees during the `test` phase.

Use the latest published Paramixel release for `YOUR_PARAMIXEL_VERSION`.

## First Test

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

Run tests with:

```bash
./mvnw test
```

## Badges

[![Build Status](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg)](https://github.com/paramixel/paramixel/actions)
[![Java Version](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Maven Central](https://img.shields.io/maven-central/v/org.paramixel/core)](https://central.sonatype.com/search?namespace=org.paramixel)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
