[![Build Status](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg)](https://github.com/paramixel/paramixel/actions)
[![Java Version](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Maven Central](https://img.shields.io/maven-central/v/org.paramixel/core)](https://central.sonatype.com/search?namespace=org.paramixel)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

# Paramixel

An action-based test framework for Java 17+ with composable action trees, lifecycle management, parallel execution, etc.

## Why Paramixel?

Most Java test frameworks bake test structure into the framework itself. You describe your tests with annotations — what runs, in what order, with what setup — but only in ways the framework anticipated. Need a loop that generates tests dynamically? A conditional that skips a group based on runtime state? Parallelism at one branch but sequential execution at another? You're working around the framework, not with it.

Annotations can't call methods. They can't branch. They can't compose. Every time a framework adds a new feature, it adds a new annotation — and you're stuck learning the framework's model instead of expressing your own.

Paramixel treats tests as **composable trees built with code, not annotations.** Build test plans with plain Java — loops, conditionals, dynamic generation — using `Sequential`, `Parallel`, `Lifecycle`, and `StrictSequential` actions that compose to any depth. Need something the built-in actions don't cover? Write a custom `Action` — either extend `AbstractAction` or implement `Action` directly, then implement `execute(Context)` and it composes like any other. Topology is explicit. After is guaranteed. Parallelism is per-node. The full power of Java is available at test definition time, because test plans are just code.

Paramixel runs anywhere Java runs. Use the Maven plugin for CI/CD integration, or embed the programmatic API directly — no build tool lock-in, no custom runners, no special CI configuration. Need more control? Implement the `Runner` interface to customize execution semantics, integrate with external systems, or build your own test orchestration.

**Key Benefits:**
- **Tests are composable trees built with code, not annotations** — `Sequential`, `Parallel`, `Lifecycle`, and `StrictSequential` compose to arbitrary depth, making test topology explicit
- **Programmatic test definition** — build test plans with Java code (loops, conditionals, dynamic generation) instead of declarative annotations
- **Guaranteed after with `Lifecycle`** — cleanup always runs, even on failure or skip; after errors are attached as suppressed exceptions, following try-with-resources semantics
- **Parallel execution at any depth** — embed `Parallel` nodes anywhere in the tree with per-node parallelism control
- **Fail-fast or run-all semantics** — `StrictSequential` stops on first failure; `Sequential` runs all children regardless; choose the right behavior per group
- **Write custom actions** — either extend `AbstractAction` or implement `Action` directly, then implement `execute(Context)` to define your own execution semantics; custom actions compose alongside built-in actions with zero framework changes
- **Single factory method produces the entire test plan** — one `@Paramixel.ActionFactory` method returns the full action tree; no per-method reflection at test time
- **No test class instantiation** — only the static factory method is called; state flows through `Context` attachments, not instance fields
- **Context attachments for state sharing** — each context can hold one typed object; navigate parent chain to share state across actions
- **Result tree mirrors the action tree** — every result has children, parent, timing, and status; walk the tree for aggregated or granular reporting at any level

## Documentation

For the full documentation, visit: **https://paramixel.github.io/paramixel**

The documentation site includes:
- [Quick Start Guide](https://paramixel.github.io/paramixel/docs/quick-start)
- [Usage Guide](https://paramixel.github.io/paramixel/docs/usage/action-composition)
- [Configuration Reference](https://paramixel.github.io/paramixel/docs/configuration)
- [API Reference](https://paramixel.github.io/paramixel/docs/api/intro)
- [Architecture](https://paramixel.github.io/paramixel/docs/architecture)

## Quick Start

### Add Dependency

**Maven:**
```xml
<properties>
    <paramixel.version>YOUR_PARAMIXEL_VERSION</paramixel.version>
</properties>

<dependency>
    <groupId>org.paramixel</groupId>
    <artifactId>core</artifactId>
    <version>${paramixel.version}</version>
</dependency>

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

Use the latest published Paramixel release for `YOUR_PARAMIXEL_VERSION`.

### Write Your First Test

```java
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;

public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Sequential.of("MyTest",
            Direct.of("first test", context -> {
                // test something
            }),
            Direct.of("second test", context -> {
                // test something else
            }));
    }
}
```

## Run Tests

```bash
./mvnw test
```

## Build from Source

```bash
git clone https://github.com/paramixel/paramixel.git
cd paramixel
./mvnw clean install
```

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
