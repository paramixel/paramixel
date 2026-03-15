[![Build Status](https://github.com/paramixel/paramixel/actions/workflows/build.yaml/badge.svg)](https://github.com/paramixel/paramixel/actions)
[![Java Version](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Maven Central](https://img.shields.io/maven-central/v/org.paramixel/paramixel-api)](https://central.sonatype.com/search?namespace=org.paramixel)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

# Paramixel Test Engine

A high-performance parameterized test engine for Java 17+ with automatic virtual thread optimization and advanced parallel execution.

**Key Benefits:**
- 🚀 **2-3x performance boost** with virtual threads (Java 21+)
- ⚡ **True parallel execution** with queue-based scheduling
- 🎯 **6 lifecycle hooks** vs JUnit's 2 (@Initialize, @BeforeAll, @BeforeEach, @AfterEach, @AfterAll, @Finalize)
- 📊 **Enhanced Maven reporting** with detailed execution summaries
- 🔧 **Flexible configuration** via system properties, Maven config, or properties files

---

## Quick Start

### Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-api</artifactId>
    <version>0.1.0-alpha-1</version>
    <scope>test</scope>
</dependency>
```

**Gradle:**
```kotlin
testImplementation("org.paramixel:paramixel-api:0.1.0-alpha-1")
```

### Write Your First Test

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;

@Paramixel.TestClass
public class MyTest {

    @Paramixel.ArgumentsCollector
    public static void arguments(ArgumentsCollector collector) {
        collector.addArguments("A", "B", "C");
    }

    @Paramixel.Test
    public void test(ArgumentContext ctx) {
        String arg = ctx.getArgument(String.class);
        // Your test logic here
    }
}
```

### Run Tests

```bash
./mvnw test
```

---

## Installation

### Prerequisites

- **Java 17 or higher** (Java 21+ recommended for virtual threads)
- Maven 3.9+ or Gradle 8+

### Build from Source

```bash
# Clone repository
git clone https://github.com/paramixel/paramixel.git
cd paramixel

# Build and install
./mvnw clean install
```

### Verify Installation

```bash
# Run test suite
./mvnw clean verify

# Expected: BUILD SUCCESS, all tests pass
```

---

## Features

### Performance Optimizations

- **Automatic Thread Optimization**: Uses virtual threads on Java 21+, platform threads on Java 17-20
- **Queue-Based Parallel Execution**: Optimal resource utilization with configurable concurrency
- **True Parallel Execution**: All arguments execute concurrently (not sequentially)
- **Three-Level Concurrency Cap**:
  - Total: `cores × 2` (global concurrent work cap)
  - Class: `cores` (max concurrent test classes)
  - Argument: `cores` (max concurrent arguments per class)

### Advanced Lifecycle Management

- **6 Comprehensive Hooks**: @Initialize, @BeforeAll, @BeforeEach, @AfterEach, @AfterAll, @Finalize
- **Hierarchical Context**: EngineContext → ClassContext → ArgumentContext with isolated stores
- **Guaranteed Execution**: After hooks run even after failures
- **AutoCloseable Support**: Automatic resource cleanup for test instances and arguments

### Enhanced Configuration

- **Multiple Sources**: System properties, Maven plugin config, properties files
- **Per-Class Parallelism**: Individual test classes specify their own concurrency limits
- **Tag Filtering**: Regex-based inclusion/exclusion with inheritance support

### Modern Development Experience

- **Seamless Maven Integration**: Detailed reporting and automatic test discovery
- **Java 17+ Compatibility**: Targets modern Java features
- **Production-Ready**: Built on JUnit Platform with comprehensive error handling

---

## Architecture

### Execution Model

Paramixel uses a queue-based actor system for optimal resource utilization:

```
┌───────────────────────────────────────────┐
│           Paramixel Engine                │
├───────────────────────────────────────────┤
│  Class Queue → Argument Queue → Method    │
│       ↓              ↓              ↓      │
│  Virtual Threads (Java 21+)               │
│  Platform Threads (Java 17-20)            │
└───────────────────────────────────────────┘
```

### Component Interaction

```
┌───────────────────────────────────────────────────────────────────────┐
│  Test Author Code                                                     │
│  @Paramixel.TestClass / @Paramixel.Test / @Paramixel.ArgumentsCollector│
└───────────────────────────────┬───────────────────────────────────────┘
                                │ uses
                                ▼
┌───────────────────────────────────────────────────────────────────────┐
│  paramixel-api                                                        │
│  Paramixel (annotations)  ArgumentContext  ClassContext                │
│  EngineContext  ArgumentsCollector  Store  Named  NamedValue           │
└───────────────────────────────┬───────────────────────────────────────┘
                                │ implements/depends on
                                ▼
┌───────────────────────────────────────────────────────────────────────┐
│  paramixel-engine                                                     │
│                                                                       │
│  ParamixelTestEngine (JUnit Platform TestEngine SPI)                  │
│    │                                                                  │
│    ├─ discover() → ParamixelDiscovery                                 │
│    │   ├─ scans classpath for @Paramixel.TestClass                    │
│    │   ├─ validates method signatures                                 │
│    │   ├─ invokes @Paramixel.ArgumentsCollector                       │
│    │   └─ builds descriptor tree                                      │
│    │                                                                  │
│    └─ execute() → ParamixelExecutionRuntime (virtual threads)         │
│        ├─ ParamixelConcurrencyLimiter (semaphores)                    │
│        ├─ ParamixelClassRunner (per class)                            │
│        ├─ ParamixelInvocationRunner (per argument)                    │
│        └─ ParamixelEngineExecutionListener (reporting)                │
└───────────────────────────────┬───────────────────────────────────────┘
                                │ launched by
                                ▼
┌───────────────────────────────────────────────────────────────────────┐
│  paramixel-maven-plugin                                               │
│  ParamixelMojo (goal: test, phase: test)                              │
│    ├─ Scans test-classes dir for @Paramixel.TestClass                 │
│    ├─ Fires JUnit Platform Launcher with EngineFilter=paramixel       │
│    └─ Fails build on any test failure                                 │
└───────────────────────────────────────────────────────────────────────┘
```

### Data Flow: Maven Running Tests

1. **Maven lifecycle** reaches the `test` phase. The `paramixel-maven-plugin:test` goal executes.
2. **ParamixelMojo** builds a `URLClassLoader` from test output + test classpath, then scans `.class` files for `@Paramixel.TestClass`.
3. **JUnit Platform Launcher** calls `ParamixelTestEngine.discover()`.
4. **ParamixelDiscovery** processes each selector, validates method signatures, invokes `@Paramixel.ArgumentsCollector`, and builds the descriptor tree.
5. **ParamixelTestEngine.execute()** reads configuration and creates a `ConcreteEngineContext`.
6. **ParamixelExecutionRuntime** provides a virtual-thread executor and concurrency limiter.
7. For each test class, a class-level permit is acquired and the class is submitted to the virtual-thread executor.
8. **ParamixelClassRunner** instantiates the test class, runs @Initialize, then processes each argument.
9. **ParamixelInvocationRunner** executes @BeforeEach, @Test, @AfterEach for each test method.
10. Results flow back as `TestExecutionResult` to `EngineExecutionListener`.
11. **ParamixelMojo** checks for failures and throws `MojoFailureException` if any tests failed.

### Architectural Decisions

**ADR-1: Descriptor Tree Built at Discovery Time**
Arguments are enumerated during `discover()`, not `execute()`. This allows IDE integration and report counts to be accurate before execution starts.

**ADR-2: Virtual Threads for All Concurrency**
All parallel work uses `Executors.newVirtualThreadPerTaskExecutor()`. Virtual threads eliminate thread-pool sizing concerns.

**ADR-3: Three-Level Concurrency Cap**
Three fair semaphores cap concurrency at total, class, and argument levels.

**ADR-4: Separate Context Hierarchy**
State scoping: `EngineContext -> ClassContext -> ArgumentContext`. Each has its own `Store` backed by `ConcurrentHashMap`.

**ADR-5: AutoCloseable Resources Auto-Closed**
Test class instances and argument values implementing `AutoCloseable` are closed automatically after the appropriate lifecycle phase.

**ADR-6: Custom Listener Only in Maven Invocation Mode**
When invoked by Maven, the engine substitutes its own listener for console output. In IDE mode, the standard listener is used.

**ADR-7: Flattened Method Inheritance**
Lifecycle hooks and test methods are discovered across the full class hierarchy and treated as a single flattened set.

---

## Modules

### Module Inventory

| Module               | Packaging      | Responsibility                             | Key Dependencies                                      |
|----------------------|----------------|--------------------------------------------|-------------------------------------------------------|
| `paramixel-api`      | `jar`          | Public annotations and context interfaces  | `junit-platform-commons`                              |
| `paramixel-engine`   | `jar`          | JUnit Platform TestEngine implementation   | `paramixel-api`, `junit-platform-engine`, `slf4j-api` |
| `paramixel-maven-plugin` | `maven-plugin` | Maven Mojo for test execution            | `paramixel-api`, `paramixel-engine`, `maven-plugin-api` |
| `paramixel-tests`    | `jar`          | Functional/integration tests               | `paramixel-api` (test)                                |
| `paramixel-examples` | `jar`          | Demonstrative test classes                 | `paramixel-api` (test), `testcontainers`              |
| `paramixel-benchmarks` | `jar`        | JMH performance benchmarks                 | `paramixel-api` (test), `jmh-core`                    |

### Module Dependency Graph

```
paramixel-api
     ▲
     │
paramixel-engine ──────────────────────┐
     ▲                                │
     │                                │
paramixel-maven-plugin                 │
     ▲                                │
     │                                │
paramixel-tests ──── (uses plugin) ────┤
paramixel-examples ── (uses plugin) ───┤
paramixel-benchmarks <─────────────────┘
```

### Technology Stack

| Concern              | Technology                                  |
|----------------------|---------------------------------------------|
| Language             | Java 21 (compiled with `--release 21`)      |
| Build tool           | Apache Maven 3.9+ with `./mvnw` wrapper     |
| Test platform        | JUnit Platform 6.0.3                        |
| Assertions           | AssertJ 3.27.7                              |
| Logging              | `java.util.logging` (JUL) + SLF4J API 2.0.17|
| Concurrency          | Java 21 virtual threads                     |
| Code formatting      | Spotless + Palantir Java Format 2.87.0      |
| Code coverage        | JaCoCo 0.8.14                               |
| Table output         | `ascii-table` 1.9.0                         |
| Null safety          | `org.jspecify` (`@NonNull`)                 |
| Integration examples | Testcontainers 2.0.3                        |

### Repository Layout

```
paramixel/
├── api/                       Public API jar — annotations + context interfaces
│   └── src/main/java/org/paramixel/api/
├── engine/                    Core test engine jar
│   └── src/main/java/org/paramixel/engine/
│       ├── api/               Concrete context/store implementations
│       ├── descriptor/        JUnit Platform TestDescriptor hierarchy
│       ├── discovery/         Test class scanner
│       ├── execution/         Runners + concurrency primitives
│       ├── filter/            Tag-based test filtering
│       ├── invoker/           Reflective method invoker
│       ├── listener/          Execution event listeners / reporters
│       ├── util/              FastIdUtil generator
│       └── validation/        Method signature validator
├── maven-plugin/              Maven Mojo (goal: test)
│   └── src/main/java/org/paramixel/maven/plugin/
├── tests/                     Functional test suite
│   └── src/test/java/test/
├── examples/                  Illustrative tests including Testcontainers
│   └── src/test/java/examples/
├── benchmarks/                JMH performance benchmarks
│   └── src/main/java/org/paramixel/engine/
├── assets/
│   └── license-header.txt     Apache 2.0 license header for Spotless
├── .github/workflows/
│   └── build.yaml             CI: push + PR → ./mvnw -B clean verify
├── paramixel.properties       Project-root properties (loaded by engine at runtime)
├── mvnw / .mvn/               Maven wrapper
└── pom.xml                    Parent POM
```

---

## Usage Guide

### Basic Test Class

```java
@Paramixel.TestClass
public class ParameterizedTest {

    @Paramixel.ArgumentsCollector
    public static void arguments(ArgumentsCollector collector) {
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

Paramixel provides 6 lifecycle hooks with guaranteed pairing:

```java
@Paramixel.TestClass
public class LifecycleTest {

    @Paramixel.ArgumentsCollector
    public static void arguments(ArgumentsCollector collector) {
        collector.addArguments("arg1", "arg2", "arg3");
    }
    
    @Paramixel.Initialize
    public void initialize(ClassContext context) {
        // Called once per class before any test methods
        // Paired with @Finalize
    }

    @Paramixel.BeforeAll
    public void beforeAll(ArgumentContext context) {
        // Called once per argument before all test methods
        // Paired with @AfterAll
    }

    @Paramixel.BeforeEach
    public void beforeEach(ArgumentContext context) {
        // Called before each test method
        // Paired with @AfterEach
    }

    @Paramixel.Test
    public void test(ArgumentContext context) {
        // Test implementation
    }

    @Paramixel.AfterEach
    public void afterEach(ArgumentContext context) {
        // Called after each test method (even on failure)
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

### Execution Order

```
[Per class]
  @Paramixel.ArgumentsCollector (static)
  TestClass.newInstance()         (no-arg constructor)
  @Paramixel.Initialize(ClassContext)
  [Per argument]
    @Paramixel.BeforeAll(ArgumentContext)
    [Per test method]
      @Paramixel.BeforeEach(ArgumentContext)
      @Paramixel.Test(ArgumentContext)
      @Paramixel.AfterEach(ArgumentContext)
    [End per test method]
    @Paramixel.AfterAll(ArgumentContext)
    argument.close()              (if implements AutoCloseable)
  [End per argument]
  @Paramixel.Finalize(ClassContext)
  testInstance.close()            (if implements AutoCloseable)
[End per class]
```

### Annotation Contracts

| Annotation          | Target       | Required Signature                           | Notes                                          |
|---------------------|--------------|----------------------------------------------|------------------------------------------------|
| `@TestClass`        | TYPE         | N/A                                          | Marks class for discovery; requires no-arg constructor |
| `@Test`             | METHOD       | `public void method(ArgumentContext)`        | Instance method, NOT static                    |
| `@ArgumentsCollector` | METHOD     | `public static void method(ArgumentsCollector)` | Static, only one per hierarchy               |
| `@Initialize`       | METHOD       | `public void method(ClassContext)`           | Instance, NOT static; paired with @Finalize    |
| `@BeforeAll`        | METHOD       | `public void method(ArgumentContext)`        | Static or instance; paired with @AfterAll      |
| `@BeforeEach`       | METHOD       | `public void method(ArgumentContext)`        | Instance, NOT static; paired with @AfterEach   |
| `@AfterEach`        | METHOD       | `public void method(ArgumentContext)`        | Instance, NOT static                           |
| `@AfterAll`         | METHOD       | `public void method(ArgumentContext)`        | Static or instance                             |
| `@Finalize`         | METHOD       | `public void method(ClassContext)`           | Instance, NOT static                           |
| `@Disabled`         | TYPE, METHOD | N/A                                          | Skips class or method during discovery         |
| `@DisplayName`      | TYPE, METHOD | `String value()`                             | Replaces default display name                  |
| `@Order`            | METHOD       | `int value()` (> 0)                          | Execution order; forces sequential execution   |
| `@Tags`             | TYPE         | `String[] value()`                           | Categorizes for filtering; inherited           |

### Named Arguments

```java
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

### Parallelism Configuration

**Global Parallelism:**
```bash
./mvnw test -Dparamixel.parallelism=4
```

**Per-Class Parallelism:**
```java
@Paramixel.ArgumentsCollector
public static void arguments(ArgumentsCollector collector) {
    collector.setParallelism(4);
}
```

**Constraint:** Per-class parallelism cannot exceed global parallelism.

**Sequential Execution Trigger:**
When ANY `@Paramixel.Test` method has `@Paramixel.Order`, ALL test methods for that argument bucket execute sequentially (even if parallelism > 1).

### Tag Filtering

Filter tests using regex patterns:

```bash
# Include integration tests
./mvnw test -Dparamixel.tags.include="integration-.*"

# Exclude slow tests
./mvnw test -Dparamixel.tags.exclude=".*slow.*"

# Combined filters
./mvnw test -Dparamixel.tags.include="integration" -Dparamixel.tags.exclude="slow"

# Multiple includes (OR logic)
./mvnw test -Dparamixel.tags.include="unit,fast,integration"

# Exact match
./mvnw test -Dparamixel.tags.include="^unit$"
```

**Pattern Syntax:**
- `integration` - Tags containing "integration"
- `^integration$` - Exact match "integration"
- `integration-.*` - Starts with "integration-"
- `.*-slow` - Ends with "-slow"
- `^unit$,^fast$` - Either "unit" OR "fast"
- `v1\\.0` - Literal "v1.0" (dot escaped)

**Tag Inheritance:**
```java
@Paramixel.TestClass
@Paramixel.Tags({"integration"})
public class BaseIntegrationTest {
    // Tag: integration
}

@Paramixel.TestClass
@Paramixel.Tags({"database", "slow"})
public class DatabaseIntegrationTest extends BaseIntegrationTest {
    // Tags: integration (inherited) + database, slow (declared)
    // Combined: [integration, database, slow]
}
```

**Filtering Behavior:**
1. **Include filters**: Select classes where ANY tag matches ANY include pattern
2. **Exclude filters**: Remove classes where ANY tag matches ANY exclude pattern
3. **Untagged classes**: Included only when no include filter is specified
4. **Case sensitivity**: Patterns are case-sensitive

**Configuration Precedence** (highest to lowest):
1. Command line (`-D` flags)
2. Maven plugin config (`<configuration>`)
3. Properties file (`paramixel.properties`)
4. Defaults (no filtering)

**Maven Plugin Configuration:**
```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <configuration>
        <tagsInclude>integration-.*</tagsInclude>
        <tagsExclude>.*slow.*,.*flaky.*</tagsExclude>
    </configuration>
</plugin>
```

**Properties File:**
```properties
paramixel.tags.include=integration-.*
paramixel.tags.exclude=.*slow.*
```

### CI/CD Examples

**GitHub Actions:**
```yaml
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
      - name: Run integration tests
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

**GitLab CI:**
```yaml
stages:
  - test
  - integration

unit:
  stage: test
  script:
    - ./mvnw test -Dparamixel.tags.include="unit"

integration:
  stage: integration
  script:
    - ./mvnw test -Dparamixel.tags.include="integration" -Dparamixel.tags.exclude="slow"
```

**Jenkins Pipeline:**
```groovy
pipeline {
    agent any
    
    stages {
        stage('Unit Tests') {
            steps {
                sh './mvnw test -Dparamixel.tags.include="unit,fast"'
            }
        }
        
        stage('Integration Tests') {
            steps {
                sh './mvnw test -Dparamixel.tags.include="integration"'
            }
        }
    }
}
```

### Coexisting with JUnit

Run Paramixel and JUnit tests in the same module using one of three strategies:

**Option 1: File Pattern Filtering (Recommended)**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.5</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
        </includes>
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

<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <version>0.1.0-alpha-1</version>
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

**Option 2: Package Separation**
```
src/test/java/
├── unit/                  # JUnit tests
│   └── MyServiceTest.java
└── paramixel/             # Paramixel tests
    └── MyParameterizedTest.java
```
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/unit/*Test.java</include>
        </includes>
        <properties>
            <configurationParameters>
                junit.platform.engine.exclude = paramixel
            </configurationParameters>
        </properties>
    </configuration>
</plugin>
```

**Option 3: Maven Profiles**
```xml
<profiles>
    <profile>
        <id>junit-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*Test.java</include>
                        </includes>
                        <properties>
                            <junit.platform.engine.exclude>paramixel</junit.platform.engine.exclude>
                        </properties>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
    
    <profile>
        <id>paramixel-tests</id>
        <build>
            <plugins>
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
            </plugins>
        </build>
    </profile>
</profiles>
```

**Usage:**
```bash
# Run JUnit only
./mvnw test -Pjunit-tests

# Run Paramixel only
./mvnw test -Pparamixel-tests

# Run both (default)
./mvnw test
```

### Maven Plugin Parameters

| Parameter                   | Property                           | Default           | Description                          |
|-----------------------------|------------------------------------|-------------------|--------------------------------------|
| `skipTests`                 | `paramixel.skipTests`              | `false`           | Skips all test execution             |
| `failIfNoTests`             | `paramixel.failIfNoTests`          | `true`            | Fails if no @Paramixel.TestClass found |
| `parallelism`               | `paramixel.parallelism`            | (engine default)  | Global max parallelism               |
| `verbose`                   | `paramixel.verbose`                | `false`           | Enables verbose output               |
| `tagsInclude`               | `paramixel.tags.include`           | (none)            | Regex pattern for inclusion          |
| `tagsExclude`               | `paramixel.tags.exclude`           | (none)            | Regex pattern for exclusion          |
| `summaryClassNameMaxLength` | `paramixel.summary.classNameMaxLength` | `2147483647`  | Max class name length in summary     |

---

## Configuration Reference

### Properties Table

| Property Key                     | Source                  | Default           | Description                          |
|----------------------------------|-------------------------|-------------------|--------------------------------------|
| `paramixel.parallelism`          | CLI / Maven / properties| `availableProcessors()` | Global max concurrent test classes |
| `paramixel.tags.include`         | CLI / Maven / properties| (none)            | Regex pattern for tag inclusion      |
| `paramixel.tags.exclude`         | CLI / Maven / properties| (none)            | Regex pattern for tag exclusion      |
| `paramixel.verbose`              | CLI / Maven             | `false`           | Enables verbose output               |
| `paramixel.skipTests`            | CLI / Maven             | `false`           | Skips test execution                 |
| `paramixel.failIfNoTests`        | CLI / Maven             | `true`            | Fails if no tests found              |
| `paramixel.summary.classNameMaxLength` | CLI / Maven       | `2147483647`      | Summary table class name max length  |

### Configuration Precedence

1. **Command line** (`-D` flags) - Highest
2. **Maven plugin config** (`<configuration>`)
3. **Properties file** (`paramixel.properties`)
4. **Defaults** - No filtering

### Summary Table Class Name Abbreviation

When invoked by Maven, class names can be abbreviated:

**Rules:**
1. Final segment (after last `.`) is always kept intact
2. Other segments are abbreviated to first character
3. Segments expand from right to left within max length

**Examples:**
- `foo.bar.Class` with max=11: `f.bar.Class`
- `foo.bar.Class` with max=10: `f.b.Class`
- `test.argument.ArgumentsTest` with max=20: `t.a.ArgumentsTest`

---

## Troubleshooting

### Tests Not Running

**Problem:** Expected tests don't execute

**Solution:**
1. Verify `@Paramixel.TestClass` annotation is present
2. Check pattern syntax (escape special chars)
3. Use verbose mode to see filtered tests
   ```bash
   ./mvnw test -X | grep "Tag filter"
   ```

### Pattern Not Matching

**Problem:** Pattern should match but doesn't

**Solution:**
1. Test pattern with online regex tester
2. Try simpler pattern first
3. Check case sensitivity
   ```bash
   ./mvnw test -Dparamixel.tags.include="(?i)integration"
   ```

### Inheritance Not Working

**Problem:** Parent class tags not inherited

**Solution:**
1. Ensure parent class has `@Paramixel.TestClass`
2. Verify `@Tags` annotation on parent
3. Check class hierarchy (direct extends only)

### Tests Running Twice

**Problem:** Same test file executes in both engines

**Solution:** Ensure file pattern filtering AND engine exclusion:
```xml
<includes>
    <include>**/*Test.java</include>
</includes>
<properties>
    <junit.platform.engine.exclude>paramixel</junit.platform.engine.exclude>
</properties>
```

### Paramixel Tests Not Discovered

**Problem:** Paramixel tests don't run

**Solution:**
1. Verify `@Paramixel.TestClass` annotation
2. Check Paramixel plugin is configured
3. Ensure classes are in `test-classes` directory
4. Run with verbose: `./mvnw paramixel:test -X`

### Engine Conflicts

**Problem:** JUnit Platform errors

**Solution:** Disable extension auto-detection:
```xml
<systemPropertyVariables>
    <junit.jupiter.extensions.autodetection.enabled>false</junit.jupiter.extensions.autodetection.enabled>
</systemPropertyVariables>
```

### Build Hangs

**Problem:** Build hangs after tests complete

**Solution:** Check for:
1. Unclosed resources in `@Finalize` hooks
2. Thread pool shutdown issues
3. Missing `executor.shutdown()` calls

### Invalid Regex Pattern

**Problem:** Test execution fails with regex error

**Solution:**
1. Escape special characters: `\\.`, `\\-`, etc.
2. Test pattern in online regex tester
3. Start with simple pattern, add complexity gradually

### No Matching Classes After Filtering

**Problem:** All tests filtered out

**Solution:**
1. Verify tag annotations on test classes
2. Check pattern matches actual tag values
3. Try without filters to confirm tests run
4. Use verbose mode to see filtering decisions

### Per-Class Parallelism Ignored

**Problem:** Class parallelism setting has no effect

**Solution:**
1. Verify `setParallelism()` is called in `@ArgumentsCollector`
2. Remember: per-class cannot exceed global parallelism
3. Check if `@Order` forces sequential execution

### AutoCloseable Not Closed

**Problem:** Resources not automatically closed

**Solution:**
1. Verify class/argument implements `AutoCloseable`
2. Check `close()` method signature
3. Review lifecycle order (after @AfterAll / @Finalize)

---

## Best Practices

### Tag Naming

✅ **Use descriptive tag names**: `integration-api`, `unit-core`, `perf-load`

✅ **Establish naming conventions**: Consistent prefixes make patterns easier

✅ **Document tag usage**: List available tags in project README

✅ **Start simple**: Begin with exact matches, add complexity as needed

✅ **Test patterns**: Verify patterns work before committing to CI/CD

❌ **Avoid overly complex patterns**: If pattern is hard to read, simplify

❌ **Don't over-tag**: 3-5 tags per class is typical

❌ **Mix naming conventions**: Pick one style (kebab-case, camelCase, etc.)

### Test Organization

✅ **Use file pattern filtering**: Most reliable separation method

✅ **Exclude Paramixel engine**: Always set `junit.platform.engine.exclude = paramixel`

✅ **Document test types**: Clearly mark which tests use which framework

✅ **Consider package separation**: Easier to maintain and filter

✅ **Use profiles for CI/CD**: Different environments may need different test sets

❌ **Don't rely on naming only**: `*Test.java` pattern alone isn't enough

❌ **Don't mix test types in same file**: Keep JUnit and Paramixel tests separate

❌ **Don't skip engine exclusion**: Will cause discovery failures

### Thread Safety

⚠️ **One test instance per class**: Shared across all argument invocations

✅ **Use thread-safe fields**: `ConcurrentHashMap`, `AtomicReference`, etc.

✅ **Avoid mutable shared state**: Each argument should be independent

✅ **Synchronize if needed**: For shared resources

❌ **Don't assume sequential execution**: Arguments run in parallel by default

### Performance Tuning

✅ **Use virtual threads**: Java 21+ recommended

✅ **Set appropriate parallelism**: `cores` is usually optimal

✅ **Profile with benchmarks**: Use `paramixel-benchmarks` module

✅ **Monitor resource usage**: Watch for thread starvation

❌ **Don't over-parallelize**: Too many permits causes contention

❌ **Don't ignore @Order side effect**: Forces sequential execution

### Common Anti-Patterns

❌ **Complex regex without testing**: Always verify patterns first

❌ **Ignoring AutoCloseable**: Resources must be closed

❌ **Static test state**: Breaks parallel execution

❌ **Blocking in lifecycle hooks**: Use async patterns

❌ **Swallowing exceptions**: Let engine handle failures

❌ **Manual thread management**: Use engine's virtual threads

---

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

- 🐛 **Report Bugs**: Create issues with reproduction steps
- 💡 **Suggest Features**: Share ideas for improvement
- 🔧 **Submit PRs**: Code contributions appreciated

---

## Sponsorship

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

[YourKit](https://www.yourkit.com/) supports open source projects with innovative tools for Java and .NET profiling.

YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/dotnet-profiler/), and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

---

## License

Copyright 2026-present Douglas Hoard. All Rights Reserved.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
