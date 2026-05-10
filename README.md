[![Build Status](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg)](https://github.com/paramixel/paramixel/actions)
[![Java Version](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Maven Central](https://img.shields.io/maven-central/v/org.paramixel/core)](https://central.sonatype.com/search?namespace=org.paramixel)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

# Paramixel

An action-based test framework for Java 17+ with composable action trees, lifecycle management, parallel execution, etc.

## Why Paramixel?

Annotation-based frameworks force you to express test logic through declarations that can't branch, loop, or compose. Paramixel replaces annotations with plain Java — test plans are built as composable code, giving you the full power of the language at definition time.

**Key Benefits:**
- **Composable action trees built with code** — setup/teardown, sequential and parallel children compose to arbitrary depth, making test topology explicit
- **Programmatic test definition** — build test plans with Java code (loops, conditionals, dynamic generation) instead of declarative annotations
- **Guaranteed teardown** — cleanup always runs on every node, even on failure or skip; teardown errors are attached as suppressed exceptions, following try-with-resources semantics
- **Parallel execution at any depth** — embed parallel children anywhere in the tree with per-node parallelism control
- **Fail-fast or run-all semantics** — choose per-node whether children stop on first failure or run all regardless
- **Write custom actions** — implement your own execution semantics; custom actions compose alongside built-in primitives with zero framework changes
- **Single factory method produces the entire test plan** — one static method returns the full action tree; no per-method reflection at test time
- **No test class instantiation** — only the static factory method is called; state flows through a key-value store, not instance fields
- **Key-value store for state sharing** — each node has an isolated store; navigate parent chains to share state across actions
- **Result tree mirrors the action tree** — walk the result tree for aggregated or granular reporting at any level

## Documentation

For the full documentation, visit: **https://paramixel.github.io/paramixel**

Use the version selector on the docs site to switch between versions.

- **Latest** — current development docs
- **3.y.z** — stable docs for the 3.x line

## Quick Start

### Add Dependency

```xml
<properties>
    <paramixel.version><PARAMIXEL_VERSION></paramixel.version>
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
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class MyTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Container.builder(MyTest.class.getName())
                .child(Container.builder("test1")
                        .before(setUp())
                        .child(test1())
                        .after(tearDown())
                        .build())
                .child(Container.builder("test2")
                        .before(setUp())
                        .child(test2())
                        .after(tearDown())
                        .build())
                .build();
    }

    private static Action setUp() {
        return Direct.builder("setUp")
                .execute(context -> { /* setup */ })
                .build();
    }

    private static Action test1() {
        return Direct.builder("test1")
                .execute(context -> { /* test something */ })
                .build();
    }

    private static Action test2() {
        return Direct.builder("test2")
                .execute(context -> { /* test something else */ })
                .build();
    }

    private static Action tearDown() {
        return Direct.builder("tearDown")
                .execute(context -> { /* teardown */ })
                .build();
    }
}
```

> **Note:** Action trees are built with `private static` methods so each action is easily discoverable from the IDE outline or structure view.

### Other Examples

See the [`examples/`](examples/src/main/java/examples/) module for more examples, including [Testcontainers](https://github.com/testcontainers/testcontainers-java), lifecycle, parallel execution, argument testing, store operations, and tag-based selection.

### Run with Maven

```bash
# Run all tests
./mvnw test

# Skip Paramixel tests
./mvnw test -Dparamixel.skipTests
```

### Run from the Console

Each test class has a `main` method and can be run directly from an IDE or console.

You can create a `__ParamixelRunner__` in a package to run all tests in that package and its subpackages from a single entry point. See [`__ParamixelRunner__.java`](examples/src/main/java/examples/__ParamixelRunner__.java) for an example.

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

## Contributing Notes

- Javadoc conventions for the codebase are documented in [`JAVADOCS.md`](JAVADOCS.md).

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

# Support

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

[YourKit](https://www.yourkit.com/) supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.

YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

---

Copyright 2026-present Douglas Hoard
