[![Build Status](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg)](https://github.com/paramixel/paramixel/actions)
[![Java Version](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Maven Central](https://img.shields.io/maven-central/v/org.paramixel/core)](https://central.sonatype.com/search?namespace=org.paramixel)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

# Paramixel

A tree-based test framework for Java 17+ with composable action trees.

## Why Paramixel?

Annotation-based frameworks force you to express test logic through declarations that can't branch, loop, or compose. Paramixel replaces annotations with plain Java — test plans are built as composable code, giving you the full power of the language at definition time.

**Key Benefits:**
- **Composable action trees built with code** — setup/teardown, sequential and parallel children compose to arbitrary depth, making test topology explicit
- **Programmatic test definition** — build test plans with Java code (loops, conditionals, dynamic generation) instead of declarative annotations
- **Explicit teardown** — configured `after` actions run even when setup or body actions fail or skip; `CleanUp.runAndThrow()` can aggregate teardown failures with suppressed exceptions
- **Parallel execution at any depth** — embed parallel children anywhere in the tree with per-node parallelism control
- **Fail-fast or run-all semantics** — choose per-node whether children stop on first failure or run all regardless
- **Write custom actions** — implement your own execution semantics; custom actions compose alongside built-in primitives with zero framework changes
- **Single factory method produces the entire test plan** — one static method returns the full action tree; no per-method reflection at test time
- **Optional managed instances** — use `Instance` actions when tests need fixture objects; otherwise actions run without test-class instantiation
- **Result tree mirrors the action tree** — walk the result tree for aggregated or granular reporting at any level

## Documentation

For the full documentation, visit: **https://paramixel.github.io/paramixel**

## Quick Start

### Add Dependency

```xml
<properties>
    <paramixel.version>5.1.1</paramixel.version>
</properties>

<dependency>
    <groupId>org.paramixel</groupId>
    <artifactId>core</artifactId>
    <version>${paramixel.version}</version>
</dependency>
```

See [Maven Central](https://central.sonatype.com/search?namespace=org.paramixel) for the latest published version.

### Configure the Maven Plugin

```xml
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

### Write Your First Test

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Step;

public class MyTest {

    public static void main(String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action<?> factory() {
        return Lifecycle.of(MyTest.class.getName())
                .before("setUp()", ctx -> { /* setup */ })
                .child(Step.of("test1()", ctx -> { /* test something */ }))
                .child(Step.of("test2()", ctx -> { /* test something else */ }))
                .after("tearDown()", ctx -> { /* teardown */ })
                .resolve();
    }
}
```

> **Note:** Action trees are built with `private static` methods so each action is easily discoverable from the IDE outline or structure view.

### Other Examples

See the [examples/](examples/src/main/java/examples/) module for more examples, including [Testcontainers](https://github.com/testcontainers/testcontainers-java), lifecycle, parallel execution, argument testing, store operations, and tag-based selection.

### Run with Maven

```bash
# Run all tests
./mvnw test

# Skip Paramixel tests
./mvnw test -Dparamixel.skipTests
```

### Run from the Console

Most test classes have a `main` method and can be run directly from an IDE or console.

You can create a `__ParamixelRunner__` in a package to run all tests in that package and its subpackages from a single entry point. See [__ParamixelRunner__.java](examples/src/main/java/examples/__ParamixelRunner__.java) for an example.

## Build from Source

### Prerequisites

- **Java 17+**
- **Maven 3.9+**

### Build

```bash
git clone https://github.com/paramixel/paramixel.git
cd paramixel
./mvnw clean install
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

# Support

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

[YourKit](https://www.yourkit.com/) supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.

YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

---

Copyright (c) 2026-present Douglas Hoard
