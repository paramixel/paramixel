[![Build](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg?branch=main)](https://github.com/dhoard/paramixel/actions/workflows/build.yaml)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![DCO](https://img.shields.io/badge/DCO-1.1-brightgreen.svg)](https://developercertificate.org/)

# Paramixel Test Engine

A powerful and flexible JUnit Platform-based Java test engine designed for parallel test execution with lifecycle management.

## Overview

Paramixel is a test engine built on top of JUnit Platform that provides:

- **Parallel Test Execution** - Run tests concurrently with configurable parallelism
- **Lifecycle Management** - Comprehensive before/after hooks for tests and classes
- **Parameterized Testing** - Built-in support for data-driven tests with argument suppliers
- **Maven Integration** - Seamless integration with Maven build lifecycle
- **Custom Listeners** - Detailed test execution reporting

## Architecture

The project is organized as a multi-module Maven project:

```
paramixel/
├── api/                    - Public API with annotations and context classes
├── engine/                 - Test discovery, scheduling, and execution engine
├── maven-plugin/           - Maven plugin for build integration
├── tests/                  - Tests validating test engine functionality
└── examples/               - Example tests showing test engine capabilities
```

## Quick Start

### Prerequisites

- Java 21 (Java 25+ recommended)
- Maven 3.9+

### Add Dependency

```xml
<dependency>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-api</artifactId>
    <version>0.0.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-engine</artifactId>
    <version>0.0.1</version>
    <scope>test</scope>
</dependency>
```

### Configure Maven Plugin

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.paramixel</groupId>
            <artifactId>paramixel-maven-plugin</artifactId>
            <version>0.0.1</version>
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

## Writing Tests

### Basic Test Class

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;

@Paramixel.TestClass
public class ParameterizedTest {

    @Paramixel.ArgumentsCollector
    public static void arguments(ArgumentsCollector collector) {
        // Called once to provide arguments for parameterized tests
        collector.setParallelism(4);
        collector.addArguments("arg1", "arg2", "arg3");
    }

    @Paramixel.Test
    public void test(ArgumentContext context) {
        String argument = context.getArgument(String.class);
        // Test with the provided argument
    }
}
```

### Lifecycle Hooks

 ```java
 import org.paramixel.api.Paramixel;
 import org.paramixel.api.ArgumentContext;
 import org.paramixel.api.ArgumentsCollector;
 import org.paramixel.api.ClassContext;
 
 @Paramixel.TestClass
 public class LifecycleTest {

    @Paramixel.ArgumentsCollector
    public static void arguments(ArgumentsCollector collector) {
        // Called once to provide arguments for parameterized tests
        collector.setParallelism(4);
        collector.addArguments("arg1", "arg2", "arg3");
    }
    
    @Paramixel.Initialize
    public void initialize(ClassContext context) {
        // Called once per class before any test methods
    }

    @Paramixel.BeforeAll
    public void beforeAll(ArgumentContext context) {
        // Called once per argument before all test methods
    }

    @Paramixel.BeforeEach
    public void beforeEach(ArgumentContext context) {
        // Called once per argument before each test method
    }

    @Paramixel.Test
    public void test(ArgumentContext context) {
        // Test implementation per argument
    }

    @Paramixel.AfterEach
    public void afterEach(ArgumentContext context) {
        // Called once per argument after each test method
    }

    @Paramixel.AfterAll
    public void afterAll(ArgumentContext context) {
        // Called once per argument after all test methods
    }

    @Paramixel.Finalize
    public void finalize(ClassContext context) {
        // Called once per class after all execution completes
    }
}
```

### Named Arguments

```java
import org.paramixel.api.Named;

public class TestData implements Named {
    
    private final String name;
    private final Object value;

    public TestData(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }
}
```

## Available Annotations

### Class-Level Annotations

- `@Paramixel.TestClass` - Marks a class as containing test methods
- `@Paramixel.DisplayName("name")` - Custom display name for the test class
- `@Paramixel.Disabled("reason")` - Disables the test class

### Method-Level Annotations

#### Test Execution
- `@Paramixel.Test` - Marks a method as a test method
- `@Paramixel.ArgumentsCollector` - Provides arguments for parameterized tests (parallelism is configured via `ArgumentsCollector.setParallelism(N)`)

#### Lifecycle Hooks
- `@Paramixel.Initialize` - Executed once before any other lifecycle method
- `@Paramixel.BeforeAll` - Executed once before all test methods
- `@Paramixel.BeforeEach` - Executed before each test method
- `@Paramixel.AfterEach` - Executed after each test method
- `@Paramixel.AfterAll` - Executed once after all test methods
- `@Paramixel.Finalize` - Executed once after all execution completes

#### Control
- `@Paramixel.Disabled("reason")` - Disables individual test methods
- `@Paramixel.DisplayName("name")` - Custom display name for test methods

### Tags

The `@Paramixel.Tags` annotation allows you to categorize test classes for selective execution:

```java
@Paramixel.TestClass
@Paramixel.Tags({"integration", "database", "slow"})
public class DatabaseIntegrationTest {
    // This test is tagged with "integration", "database", and "slow"
}

@Paramixel.TestClass
@Paramixel.Tags({"unit", "fast"})
public class UnitTest {
    // This test is tagged with "unit" and "fast"
}
```

**Tag Validation Requirements:**
- Tags annotation can only be used on classes annotated with `@Paramixel.TestClass`
- Tags array must contain at least one tag
- Each tag value must be non-null and non-empty (after trimming)
- At most one `@Tags` annotation is allowed per class (each class in a hierarchy can have its own `@Tags`)
- Tags are inherited from parent classes and combined when filtering
- Invalid tag usage will cause test discovery to fail with an error

**Tag Inheritance:**
When a test class extends another test class, tags from both classes are combined:

```java
@Paramixel.TestClass
@Paramixel.Tags({"integration"})
public class BaseIntegrationTest {
    // Base class with "integration" tag
}

@Paramixel.TestClass
@Paramixel.Tags({"database", "slow"})
public class DatabaseIntegrationTest extends BaseIntegrationTest {
    // Inherits "integration" tag, has its own "database" and "slow" tags
    // Combined tags: ["integration", "database", "slow"]
}
```

When filtering with `-Dparamixel.tags.include="integration"`, the `DatabaseIntegrationTest` will match because it inherits the "integration" tag from its parent class.

## Tag-Based Test Filtering

Paramixel supports filtering tests based on their `@Tags` annotations using regular expressions. This allows you to run specific subsets of tests during development or CI/CD pipelines.

### Using System Properties

```bash
# Run only integration tests
./mvnw test -Dparamixel.tags.include="integration-.*"

# Exclude slow tests
./mvnw test -Dparamixel.tags.exclude=".*slow.*"

# Include integration tests but exclude slow ones
./mvnw test -Dparamixel.tags.include="integration-.*" -Dparamixel.tags.exclude=".*slow.*"

# Include multiple patterns (OR logic)
./mvnw test -Dparamixel.tags.include="^unit$,^fast$"

# Match tags with special characters (escape regex metacharacters)
./mvnw test -Dparamixel.tags.include="v1\\.0"
```

### Using Maven Plugin Configuration

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <version>0.0.1</version>
    <configuration>
        <tagsInclude>integration-.*</tagsInclude>
        <tagsExclude>.*-slow,.*-flaky</tagsExclude>
        <summaryClassNameMaxLength>60</summaryClassNameMaxLength>
    </configuration>
</plugin>
```

`summaryClassNameMaxLength` abbreviates class names in the Maven-only `Paramixel Test Summary`
table by shortening package segments while keeping the final segment intact. This is display-only
and may cause ambiguous/colliding rendered names; increase the maximum length when you need
unambiguous output.

If the final segment alone exceeds the configured maximum, it is still kept intact and the
rendered class name may exceed the configured maximum.

Example: `foo.bar.Class`

- With `paramixel.summary.classNameMaxLength=11`: `f.bar.Class`
- With `paramixel.summary.classNameMaxLength=10`: `f.b.Class`

Example: `test.argument.ArgumentsTest`

- With `paramixel.summary.classNameMaxLength=20`: `t.a.ArgumentsTest`

### Using Properties File

Create a `paramixel.properties` file in your project root:

```properties
# Run integration tests except slow ones
paramixel.tags.include=integration-.*
paramixel.tags.exclude=.*slow.*,.*flaky.*

# Control test class parallelism (default: number of available processors)
paramixel.parallelism=4

# Limit class name width in the Maven-only summary table
paramixel.summary.classNameMaxLength=60
```

### Configuration Precedence

When a property is defined in multiple places, the following precedence applies (highest to lowest):

1. **Command line system properties** (e.g., `-Dparamixel.parallelism=8`)
2. **Properties file** (`paramixel.properties`)
3. **Default value**

### Controlling Parallelism

Paramixel uses virtual threads for concurrent test execution. The degree of parallelism can be controlled at both the global and per-class levels.

**Global Parallelism (`paramixel.parallelism`)**

The `paramixel.parallelism` property establishes the overall maximum number of concurrent test classes that may execute simultaneously across the entire test suite. This serves as the upper bound for all parallelism within the engine.

```bash
# Run with 4 concurrent test classes
./mvnw test -Dparamixel.parallelism=4
```

Or in `paramixel.properties`:
```properties
paramixel.parallelism=4
```

**Per-Class Parallelism (`ArgumentsCollector.setParallelism()`)**

Individual test classes may specify their own parallelism limit via `ArgumentsCollector.setParallelism()`. This value represents the maximum concurrency permitted for that specific test class. However, the effective parallelism for any class is constrained by the global `paramixel.parallelism` setting—the per-class value cannot exceed the global limit.

For example, if `paramixel.parallelism=4` (global) and a test class calls `setParallelism(8)` (per-class), the effective parallelism for that class will be 4.

### Regex Pattern Examples

| Pattern | Matches |
|---------|---------|
| `integration-.*` | Tags starting with "integration-" |
| `^unit$` | Exactly "unit" |
| `.*slow.*` | Tags containing "slow" |
| `^fast$\|^api$` | Either "fast" or "api" |
| `v1\\.0` | Literally "v1.0" (dot escaped) |

### Filtering Behavior

1. **Include filters** select classes where ANY tag matches ANY include pattern
2. **Exclude filters** remove classes where ANY tag matches ANY exclude pattern
3. **Untagged classes** are only included when no include filter is specified
4. **Case sensitivity**: Patterns are case-sensitive (Java regex default)

### CI/CD Example

```yaml
# GitHub Actions workflow
jobs:
  fast-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run fast unit tests
        run: ./mvnw test -Dparamixel.tags.include="unit,fast"
        
  integration-tests:
    runs-on: ubuntu-latest
    needs: fast-tests
    steps:
      - uses: actions/checkout@v4
      - name: Run integration tests (excluding slow)
        run: ./mvnw test -Dparamixel.tags.include="integration" -Dparamixel.tags.exclude="slow"
        
  database-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
    steps:
      - uses: actions/checkout@v4
      - name: Run database tests
        run: ./mvnw test -Dparamixel.tags.include="database"
```

## Building from Source

**Requirements:**
- **Java 21 or higher** is required to build from source
- Maven 3.9+

```bash
# Clean and build all modules
./mvnw clean verify

# Install to local Maven repository
./mvnw clean install

# Build specific module
./mvnw clean verify -pl api

# Run tests only
./mvnw clean test
```

## Configuration

### Maven Plugin Configuration

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <version>0.0.1</version>
    <configuration>
        <verbose>true</verbose>
        <failIfNoTests>false</failIfNoTests>
    </configuration>
    <executions>
        <execution>
            <phase>test</phase>
            <goals>
                <goal>test</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Parallelism

Control parallelism using `ArgumentsCollector.setParallelism(...)` inside your `@Paramixel.ArgumentsCollector`:

```java
@Paramixel.ArgumentsCollector
public static void arguments(ArgumentsCollector collector) {
    collector.setParallelism(4); // Up to 4 concurrent invocations
    collector.addArgument(...)
}
```

## Modules

### API (`paramixel-api`)

Provides the public API including:
- Annotations (`@TestClass`, `@Test`, `@BeforeAll`, etc.)
- Context classes (`ArgumentContext`, `ClassContext`, `EngineContext`)
- Support interfaces (`Named`)

### Engine (`paramixel-engine`)

Core test execution engine:
- Test discovery and filtering
- Parallel test scheduling
- Lifecycle method execution
- JUnit Platform integration

### Maven Plugin (`paramixel-maven-plugin`)

Maven build integration:
- Automatic test discovery
- Test execution during Maven test phase
- Detailed execution reporting

### Tests (`paramixel-tests`)

Sample tests demonstrating:
- Basic test structure
- Parameterized tests
- Lifecycle hooks
- Parallel execution

### Examples (`paramixel-examples`)

Practical examples showcasing test engine capabilities:
- **Simple Tests** - Basic sequential and parallel argument tests
- **Complex Tests** - Advanced parameterized test patterns
- **Testcontainers Integration** - Integration tests with Docker containers (Nginx, Kafka, MongoDB)
- **Resource Management** - Examples of test resource handling

The examples module serves as a reference for writing tests with various complexity levels and integration patterns.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## Support

For questions and support:
- Create an issue in the GitHub repository
- Check the tests in the `tests/` module
- Check the sample examples in the `examples/` module
- Review the API documentation in the `api/` module

## Sponsorship

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

[YourKit](https://www.yourkit.com/) supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.

YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.****

## License

Copyright 2026-present Douglas Hoard. All Rights Reserved.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
