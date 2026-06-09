[![Build Status](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg)](https://github.com/paramixel/paramixel/actions)
[![Java Version](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Maven Central](https://img.shields.io/maven-central/v/org.paramixel/core)](https://central.sonatype.com/search?namespace=org.paramixel)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

# Paramixel

Paramixel turns complex Java tests into readable, composable execution trees.

It is a Java 17+ test orchestration framework for integration tests, compatibility scenarios, lifecycle-heavy workflows, and test plans that are awkward to express as flat annotation-driven test methods.

## Why Paramixel?

Most Java testing frameworks are method-based. That is a great fit for ordinary unit tests.

Some tests, however, are naturally structured:

- setup must wrap a group of related checks
- teardown must run even when setup or body steps fail
- some branches must run sequentially
- some branches may run in parallel
- scenarios are generated from plain Java code
- the final result should show the same structure that was executed

Paramixel models those tests as executable action trees. The tree you build is the tree Paramixel runs, and the result tree mirrors that same structure.

**Key benefits:**

- **Composable execution trees built with plain Java** — setup/teardown, sequential children, and parallel branches compose to arbitrary depth, making execution structure explicit
- **Dynamic test definition** — build test plans with Java loops, conditionals, and generated data while using annotations only where they add value
- **Explicit teardown** — configured `after` actions run even when setup or body actions fail or skip; `CleanUp.runAndThrow()` can aggregate teardown failures with suppressed exceptions
- **Parallel execution at any depth** — embed parallel children anywhere in the tree with per-node parallelism control
- **Fail-fast or run-all semantics** — choose per-node whether children stop on first failure or run all regardless
- **Write custom actions** — implement your own execution semantics; custom actions compose alongside built-in primitives with zero framework changes
- **Single factory method produces the entire test plan** — one static method returns the full action tree; no per-method reflection at test time
- **Optional managed instances** — use `Instance` actions when tests need fixture objects; otherwise actions run without test-class instantiation
- **Inspectable results** — walk the result tree for aggregated or granular reporting at any level

## When to use Paramixel

Use Paramixel when the structure of the test matters as much as the assertions.

Good fits include:

- integration test workflows
- compatibility testing across versions, configurations, or environments
- dynamically generated test plans
- lifecycle-heavy tests with nested setup and teardown
- tests that mix sequential and parallel execution
- test suites where the result should be inspectable as a tree

Paramixel is probably unnecessary when:

- the test is a simple unit test
- one test method clearly expresses the behavior
- JUnit parameterized tests are sufficient
- no explicit lifecycle or execution structure is needed

## How Paramixel complements JUnit

JUnit is excellent for ordinary method-based tests. Paramixel can run alongside JUnit in the same project and build, and is designed for cases where a test is better represented as a tree: global setup/teardown, nested sequential and parallel branches, per-branch lifecycle, generated environments, retries, timeouts, isolation, and reports that mirror the structure being executed.

| Need | JUnit-style method tests | Paramixel |
| --- | --- | --- |
| Simple unit test | Excellent fit | Usually unnecessary |
| Annotation-driven discovery | Excellent fit | Optional factory discovery |
| Complex workflow | Possible, but can become awkward | Natural fit |
| Nested setup/teardown | Usually indirect | Explicit in the tree |
| Dynamic test generation | Supported, but framework-shaped | Plain Java code |
| Sequential + parallel sections | Usually externalized | Built into the action tree |
| Result mirrors execution structure | Limited | Core model |

A Kafka compatibility test is a good example. A single Paramixel factory can build a matrix of Kafka versions, run version-specific environments in parallel, wrap each environment with its own setup and teardown, run nested producer/consumer checks inside each environment, and wrap the whole run with global hooks. JUnit can approximate parts of this with extensions or custom engines, but Paramixel exposes the execution tree directly as the core model.

## Documentation

For the full documentation, visit: **https://www.paramixel.org**

## Quick Start

This quick start creates a small Paramixel execution tree with setup, two test steps, and teardown.

The important idea is that the Java code builds the same structure that Paramixel executes and reports.

### Add Dependency

```xml
<properties>
    <paramixel.version>6.1.0</paramixel.version>
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
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

public final class MyTest {

    public static void main(String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action factory() {
        return Scope.builder(MyTest.class.getName())
                .before(Step.of("setUp()", ctx -> { /* setup */ }))
                .body(Sequence.builder("tests")
                        .child(Step.of("test1()", ctx -> { /* test something */ }))
                        .child(Step.of("test2()", ctx -> { /* test something else */ }))
                        .build())
                .after(Step.of("tearDown()", ctx -> { /* teardown */ }))
                .build();
    }
}
```

This creates the following execution tree:

```text
MyTest
├── before: setUp()
├── body: tests
│   ├── test1()
│   └── test2()
└── after: tearDown()
```

Paramixel executes the tree and returns a result tree with the same shape.

> **Note:** Keep factory methods public and static for discovery. Use small private helper methods when that makes each action easy to find from the IDE outline or structure view.

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
