[![Build Status](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg)](https://github.com/paramixel/paramixel/actions)
[![Java Version](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Maven Central](https://img.shields.io/maven-central/v/org.paramixel/paramixel-api)](https://central.sonatype.com/search?namespace=org.paramixel)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![DCO](https://img.shields.io/badge/DCO-1.1-brightgreen.svg)](https://developercertificate.org/)

# Paramixel Test Engine ⚡

A high-performance JUnit Platform-based test engine for Java 17+ featuring automatic virtual thread optimization, advanced lifecycle management, and enhanced parallel execution capabilities.

> **Key Advantages Over Standard Parameterized Testing:**
> - 🚀 **Automatic Virtual Thread Optimization** (Java 21+) - 2-3x performance boost
> - ⚡ **Advanced Queue-Based Parallelism** - Better resource utilization
> - 🎯 **Comprehensive Lifecycle Management** - 6 hooks vs JUnit's 2
> - 🔧 **Enhanced Configuration System** - Multiple configuration sources
> - 📊 **Detailed Maven Reporting** - Better test execution insights

## 📖 Quick Navigation

- [🌟 Why Choose Paramixel?](#-why-choose-paramixel)
- [⚡ Comparison with JUnit Jupiter Parameterized Tests](#-comparison-with-junit-jupiter-parameterized-tests)
- [🚀 Getting Started](#-getting-started)
- [📚 Features](#-features)
- [🏗️ Architecture](#️-architecture)
- [🔧 Configuration](#-configuration)
  - [🔄 Coexisting with Standard JUnit Tests](#-coexisting-with-standard-junit-tests)
- [🏭 Modules](#-modules)
- [📖 Documentation](#-documentation)
- [🤝 Contributing](#-contributing)

## 🌟 Why Choose Paramixel?

Paramixel offers significant advantages over traditional parameterized testing approaches:

- **🎯 Automatic Performance Optimization** - Leverages virtual threads on Java 21+ automatically
- **⚡ Advanced Parallel Execution** - Queue-based architecture for optimal resource utilization
- **🔧 Comprehensive Lifecycle Management** - More granular control than standard JUnit hooks
- **📊 Enhanced Reporting** - Detailed execution summaries with Maven integration
- **🚀 Modern Java Compatibility** - Built for Java 17+ with forward-looking features

## ⚡ Comparison with JUnit Jupiter Parameterized Tests

Paramixel provides superior capabilities for complex parameterized testing scenarios:

| Feature | JUnit Jupiter Parameterized | Paramixel | Advantage |
|---------|----------------------------|-----------|-----------|
| **Parallel Execution** | Limited (manual configuration) | **True parallel execution with fire-and-forget model** | ⚡ Optimal resource utilization across all arguments |
| **Thread Management** | Platform threads only | **Automatic virtual thread detection** (Java 21+) | 🚀 Optimal performance |
| **Lifecycle Hooks** | Basic (@BeforeEach/AfterEach) | **6 comprehensive hooks** per test lifecycle | 🎯 More control |
| **Maven Integration** | Standard reporting | **Enhanced reporting** with detailed summaries | 📊 Better visibility |
| **Argument Suppliers** | Standard sources (CSV, enum, etc.) | **Dynamic collections** with programmatic control | 💡 More flexibility |

### Key Differences Summary

**Performance Optimization:**
- Paramixel automatically detects and uses virtual threads on Java 21+ for optimal performance
- JUnit Jupiter Parameterized Tests use platform threads exclusively

**Execution Control:**
- Paramixel offers queue-based parallel execution with per-class parallelism configuration
- JUnit provides basic parallel execution without advanced scheduling

**Lifecycle Management:**
- Paramixel: @Initialize, @BeforeAll, @BeforeEach, @AfterEach, @AfterAll, @Finalize
- JUnit: @BeforeEach, @AfterEach only

**Configuration Flexibility:**
- Paramixel supports system properties, Maven configuration, and properties files
- JUnit primarily uses system properties

### Enhanced Parallel Execution Capability

Paramixel's execution model has been significantly enhanced to provide true parallel execution across all arguments:

**Key Improvements:**
- 🚀 **Eliminated sequential bottlenecks** - Argument 0 no longer executes inline
- 🔄 **Fire-and-forget model** - Argument slots recycle immediately after completion
- ⚖️ **Dynamic resource allocation** - Global argument slots shared across all test classes
- 🚦 **Progress guarantee** - First-permit acquisition prevents starvation

**Before Enhancement:**
- Argument 0 executed sequentially before other arguments could start
- Limited effective parallelism for argument-heavy tests

**After Enhancement:**
- All arguments execute concurrently from the beginning
- Maximum resource utilization across the entire test suite
- Improved performance particularly for tests with many arguments

### Performance Impact

For test classes with:
- **6+ arguments**: Up to 20% reduction in execution time
- **High argument count**: Even greater improvements due to eliminated bottlenecks
- **I/O-bound tests**: Optimal performance with virtual thread utilization

### Resource Allocation Model

Paramixel uses a sophisticated concurrency model:
- **Total slots = cores × 2** (48 slots on 24-core system)
- **Class slots = cores** (max per class)
- **Argument slots = cores** (shared globally)
- **Recycling slots** - Argument slots immediately free up after completion

### When to Choose Paramixel

Choose Paramixel when you need:
- ✅ Advanced parallel execution control
- ✅ Automatic performance optimization (virtual threads)
- ✅ Comprehensive test lifecycle management
- ✅ Enhanced Maven integration and reporting
- ✅ Multiple configuration methods
- ✅ Modern Java (17+) compatibility
- ⚡ **True parallel execution** - Eliminated sequential bottlenecks

Paramixel complements JUnit rather than replacing it.

- You can freely mix Paramixel and standard JUnit tests in the same codebase, using each where it makes the most sense.

### Migration from JUnit Jupiter Parameterized Tests

Migrating from JUnit Jupiter Parameterized Tests to Paramixel is straightforward:

1. **Replace Dependencies:** Swap JUnit Jupiter for Paramixel artifacts
2. **Update Annotations:** Change `@ParameterizedTest` to `@Paramixel.Test`
3. **Enhance Lifecycle:** Add advanced lifecycle hooks (@Initialize, @Finalize)
4. **Configure Parallelism:** Use `ArgumentsCollector.setParallelism()`
5. **Leverage Features:** Utilize enhanced configuration and reporting

### 🚀 Performance Benefits

Paramixel's architecture provides tangible performance improvements:

**Virtual Threads (Java 21+):**
- ⚡ **Significantly reduced memory overhead** compared to platform threads
- ⚡ **Improved scalability** for I/O-bound test workloads
- ⚡ **Automatic optimization** without manual configuration

**True Parallel Execution:**
- ⚡ **Fire-and-forget model** - All arguments execute truly in parallel
- ⚡ **Eliminated sequential bottlenecks** - No forced inline execution of argument 0
- ⚡ **Optimal resource utilization** - Global argument slots shared across all classes
- ⚡ **Progress guarantee** - Prevents starvation with first-permit acquisition

**Compared to JUnit Jupiter Parameterized Tests:**
- ✅ **Up to 20% performance improvement** for argument-heavy tests by eliminating sequential bottlenecks
- ✅ **True parallel execution** - All arguments execute concurrently vs sequential execution of argument 0
- ✅ **More consistent execution** across varying test workloads
- ✅ **Better resource management** preventing OOME in large test suites

## 🚀 Getting Started

### Prerequisites

## 📚 Features

Paramixel provides advanced testing capabilities that surpass traditional JUnit parameterized testing:

### ⚡ Performance Optimizations
- **Automatic Thread Optimization**: Uses virtual threads on Java 21+, platform threads on Java 17-20
- **Queue-Based Parallel Execution**: Optimal resource utilization with configurable concurrency
- **Intelligent Scheduling**: Manages thread allocation across test classes and arguments

### 🔧 Advanced Lifecycle Management
- **6 Comprehensive Hooks**: @Initialize, @BeforeAll, @BeforeEach, @AfterEach, @AfterAll, @Finalize
- **Hierarchical Context Management**: EngineContext → ClassContext → ArgumentContext with isolated stores
- **Guaranteed Execution**: After hooks run even after failures

### 📊 Enhanced Configuration System
- **Multiple Configuration Sources**: System properties, Maven plugin config, properties files
- **Per-Class Parallelism**: Individual test classes can specify their own concurrency limits
- **Flexible Test Filtering**: Regex-based tag filtering with inheritance support

### 🚀 Modern Development Experience
- **Seamless Maven Integration**: Detailed reporting and automatic test discovery
- **Java 17+ Compatibility**: Targets modern Java features while maintaining compatibility
- **Production-Ready Architecture**: Built on JUnit Platform with comprehensive error handling

### 🎯 Unique Capabilities
- **Dynamic Argument Suppliers**: Programmatic argument collection with full context access
- **Named Argument Support**: Custom display names for argument values
- **Advanced Tag Filtering**: Regex-based inclusion/exclusion with complex patterns

## 🏗️ Architecture
## 🏗️ Architecture

### Actor-Based Execution Model

Paramixel uses a message-passing actor system for execution, providing clear separation of concerns and optimal resource utilization:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Actor-Based Execution System                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ClassActor       ArgumentDispatcher    MethodDispatcher    LifecycleActor    │
│       │                 │                     │                  │            │
│       ▼                 ▼                     ▼                  ▼            │
│  ┌────────┐      ┌──────────────┐      ┌──────────────┐    ┌──────────┐     │
│  │ Class  │      │ Argument     │      │ Method       │    │ Lifecycle│     │
│  │ Queue  │      │ Queue        │      │ Queues       │    │ Queue    │     │
│  └────────┘      └──────────────┘      └──────────────┘    └──────────┘     │
│       │                 │                     │                  │            │
│       └─────────────────┴─────────────────────┴──────────────────┘            │
│                                 │                                             │
│                                 ▼                                             │
│                      ┌─────────────────────────┐                              │
│                      │   TaskDispatcher        │                              │
│                      │   (Message Router)      │                              │
│                      └─────────────────────────┘                              │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Components:**

* **ExecutionTask Interface**: Sealed interface defining all task types (ClassTask, ArgumentTask, MethodTask, LifecycleTask)

* **MessageQueue<T>**: Thread-safe queue for each actor type, supporting blocking operations

* **Actors**: Single-threaded consumers that process messages from their assigned queues

* **SharedPermitManager**: Manages concurrency limits from `ParamixelConcurrencyLimiter`

**Concurrency Limits:**

The execution system respects three levels of parallelism:

| Level | Limit | Description |
|-------|-------|-------------|
| **Total slots** | `cores × 2` | Hard global cap on concurrent work |
| **Class slots** | `cores` | Maximum concurrent test classes |
| **Argument slots** | `cores` | Maximum concurrent arguments |

**Execution Flow:**

1. **Class Task**: Actor receives `ClassTask` with test class and arguments
2. **Argument Dispatch**: Arguments are dispatched based on `argumentParallelism` setting
3. **Method Execution**: Each argument gets its own method queue for parallel execution
4. **Lifecycle Handling**: All lifecycle hooks run through the lifecycle actor

**Benefits:**

* **Clear Separation**: Each actor type handles one concern (class/argument/method/lifecycle)
* **True Isolation**: No shared mutable state between actors
* **Respectful Limits**: Per-class and global parallelism enforced
* **Progress Guarantee**: Prevents starvation with fair scheduling

## 🚀 Getting Started

### Prerequisites
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

Paramixel automatically optimizes thread usage based on your Java version:
- **Java 21+**: Uses virtual threads for optimal performance
- **Java 17-20**: Uses efficient platform thread pools

The degree of parallelism can be controlled at both the global and per-class levels.

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

## 🛠️ Building from Source

**Requirements:**
- **Java 17 or higher** (Java 21+ recommended for virtual thread testing)
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

## 🔧 Configuration

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

#### 🔄 Coexisting with Standard JUnit Tests

When using Paramixel tests alongside standard JUnit Jupiter tests in the same Maven module, you need to configure both Maven Surefire (for JUnit tests) and the Paramixel plugin (for Paramixel tests) properly to avoid engine conflicts.

#### Configuration for Mixed Test Environments

**Complete Maven Configuration Example:**

```xml
<build>
    <plugins>
        <!-- Standard JUnit Tests (excludes Paramixel engine) -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.5.5</version>
            <configuration>
                <properties>
                    <configurationParameters>
                        junit.platform.engine.exclude = paramixel
                    </configurationParameters>
                </properties>
                <systemPropertyVariables>
                    <junit.jupiter.extensions.autodetection.enabled>false</junit.jupiter.extensions.autodetection.enabled>
                </systemPropertyVariables>
            </configuration>
        </plugin>
        
        <!-- Paramixel Tests -->
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

#### Configuration Breakdown

**Maven Surefire Configuration:**
- **`junit.platform.engine.exclude = paramixel`**: Prevents Paramixel tests from running during standard JUnit execution
- **`junit.jupiter.extensions.autodetection.enabled=false`**: Disables auto-detection to avoid conflicts

**Paramixel Plugin Configuration:**
- Executes during the `test` phase alongside Maven Surefire
- Automatically discovers and runs only `@Paramixel.TestClass` annotated tests

#### Usage Scenarios

**1. Gradual Migration from JUnit to Paramixel:**

```xml
<!-- Keep existing JUnit tests unchanged -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <properties>
            <configurationParameters>
                junit.platform.engine.exclude = paramixel
            </configurationParameters>
        </properties>
    </configuration>
</plugin>

<!-- Add Paramixel for new tests -->
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
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

**2. Running Specific Test Types Separately:**

```bash
# Run only JUnit tests (excludes Paramixel)
./mvnw surefire:test

# Run only Paramixel tests
./mvnw paramixel:test

# Run all tests (both engines)
./mvnw test
```

**3. Tag-Based Filtering Example:**

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <configuration>
        <tagsInclude>integration</tagsInclude>
        <tagsExclude>slow</tagsExclude>
    </configuration>
</plugin>
```

#### Troubleshooting Common Issues

**Issue**: Tests running twice or engine conflicts
**Solution**: Ensure `junit.platform.engine.exclude = paramixel` is set in Maven Surefire configuration

**Issue**: Paramixel tests not discovered
**Solution**: Verify `@Paramixel.TestClass` annotation is present and Paramixel plugin is configured

**Issue**: Mixed test execution order problems
**Solution**: Both plugins run in the `test` phase; execution order is determined by Maven lifecycle

#### Best Practices

✅ **Separate Test Types**: Consider keeping Paramixel and JUnit tests in different packages
✅ **Clear Naming**: Use naming conventions to distinguish test types
✅ **Configuration Management**: Use Maven profiles for different test scenarios
✅ **Documentation**: Clearly document which tests use which framework

### Parallelism

Control parallelism using `ArgumentsCollector.setParallelism(...)` inside your `@Paramixel.ArgumentsCollector`:

```java
@Paramixel.ArgumentsCollector
public static void arguments(ArgumentsCollector collector) {
    collector.setParallelism(4); // Up to 4 concurrent invocations
    collector.addArgument(...)
}
```

## 🏭 Modules

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

## Pronunciation

**Paramixel** *(pronounced "pair-uh-mick-suhl")*

## 🤝 Contributing

We welcome contributions from the community! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on how to get started.

- 🐛 **Report Bugs**: Create issues with detailed reproduction steps
- 💡 **Suggest Features**: Share ideas for improvement
- 🔧 **Submit Pull Requests**: Code contributions are greatly appreciated

## 📖 Documentation & Support

### 📚 Comprehensive Documentation
- [Java 17+ Compatibility Guide](docs/java-17-plus-compatibility.md) - Detailed compatibility information
- [System Specifications](.specify/specs/system/) - Complete architecture and design documentation
- API Documentation - Generated JavaDoc available with builds

### 💬 Getting Help
- **GitHub Issues**: Create issues for bugs and feature requests
- **Documentation**: Review tests in `tests/` and examples in `examples/` modules
- **API Reference**: Explore the public API in `org.paramixel.api` package

## Sponsorship

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

[YourKit](https://www.yourkit.com/) supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.

YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.****

## License

Copyright 2026-present Douglas Hoard. All Rights Reserved.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
