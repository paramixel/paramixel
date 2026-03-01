# Paramixel Test Engine

A powerful and flexible JUnit platform based Java test engine designed for parallel test execution with lifecycle management.

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
    <groupId>com.paramixel</groupId>
    <artifactId>paramixel-api</artifactId>
    <version>0.0.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.paramixel</groupId>
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
            <groupId>com.paramixel</groupId>
            <artifactId>paramixel-maven-plugin</artifactId>
            <version>0.0.1</version>
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

## Writing Tests

### Basic Test Class

```java
import com.paramixel.api.Paramixel;
import com.paramixel.api.context.ArgumentContext;

@Paramixel.TestClass
public class MyTest {

    @Paramixel.Test
    public void testSomething(ArgumentContext context) {
        // Test implementation
    }
}
```

### Parameterized Tests

```java
import com.paramixel.api.Paramixel;
import com.paramixel.api.context.ArgumentContext;
import java.util.Arrays;
import java.util.Collection;

@Paramixel.TestClass
public class ParameterizedTest {

    @Paramixel.ArgumentSupplier(parallelism = 4)
    public static Collection<String> provideArguments() {
        return Arrays.asList("arg1", "arg2", "arg3");
    }

    @Paramixel.Test
    public void testWithArgument(ArgumentContext context) {
        String argument = (String) context.getArgument();
        // Test with the provided argument
    }
}
```

### Lifecycle Hooks

```java
import com.paramixel.api.Paramixel;
import com.paramixel.api.context.ArgumentContext;
import com.paramixel.api.context.ClassContext;

@Paramixel.TestClass
public class LifecycleTest {

    @Paramixel.Initialize
    public void initialize(ClassContext context) {
        // Called once before any test methods
    }

    @Paramixel.BeforeAll
    public void setupAll(ArgumentContext context) {
        // Called once before all test methods
    }

    @Paramixel.BeforeEach
    public void setupEach(ArgumentContext context) {
        // Called before each test method
    }

    @Paramixel.Test
    public void testMethod(ArgumentContext context) {
        // Test implementation
    }

    @Paramixel.AfterEach
    public void teardownEach(ArgumentContext context) {
        // Called after each test method
    }

    @Paramixel.AfterAll
    public void teardownAll(ArgumentContext context) {
        // Called once after all test methods
    }

    @Paramixel.Finalize
    public void finalize(ClassContext context) {
        // Called once after all execution completes
    }
}
```

### Named Arguments

```java
import com.paramixel.api.argument.Named;

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
- `@Paramixel.ArgumentSupplier(parallelism = N)` - Provides arguments for parameterized tests

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
    <groupId>com.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <version>0.0.1</version>
    <configuration>
        <verbose>true</verbose>
        <failIfNoTests>false</failIfNoTests>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>test</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Parallelism

Control parallelism using the `@Paramixel.ArgumentSupplier` annotation:

```java
@Paramixel.ArgumentSupplier(parallelism = 4)  // Up to 4 concurrent invocations
public static Collection<Arguments> provideArguments() {
    // ...
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

## License

Copyright (C) Douglas Hoard. All Rights Reserved.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

