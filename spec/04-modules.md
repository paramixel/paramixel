# Paramixel — Module Guide

---

## Module: `paramixel-api`

### Responsibility

Defines the **public contract** between test authors and the engine. Contains only annotations, interfaces, and one concrete value class. Has zero engine implementation code.

### Internal Package Structure

```
org.paramixel.api
├── Paramixel.java                 All lifecycle annotations as nested types
├── ArgumentContext.java           Interface: per-invocation context
├── ArgumentsCollector.java   Interface: argument registration + parallelism
├── ClassContext.java              Interface: per-class context
├── EngineContext.java             Interface: engine-level config context
├── Named.java                     Interface: argument display name provider
├── NamedValue.java                Concrete: generic name+value wrapper (implements Named)
└── Store.java                     Interface: scoped key-value store
```

### Layer Breakdown

| Layer | Contents |
|---|---|
| Public API | All files above — no sub-packages |
| Test sources | `org.paramixel.api.NamedValueTest` (unit test for `NamedValue`) |

### Key Classes and Roles

| Class | Role |
|---|---|
| `Paramixel` | Container for all nested annotation types; primary entry point for test authors |
| `ArgumentContext` | Context injected into `@BeforeAll`, `@BeforeEach`, `@Test`, `@AfterEach`, `@AfterAll` methods |
| `ClassContext` | Context injected into `@Initialize` and `@Finalize` methods |
| `EngineContext` | Root context; provides configuration properties and engine-scoped store |
| `ArgumentsCollector` | Passed to collector-driven `@Paramixel.ArgumentsCollector` methods |
| `Store` | Scoped state-sharing interface used at engine, class, and argument levels |
| `Named` | Interface argument objects may implement to provide a readable display name |
| `NamedValue<T>` | Concrete wrapper combining a name and a typed payload |

### What This Module MUST NOT Do

- Must NOT contain any engine implementation code (no `TestEngine`, no schedulers, no reflection logic).
- Must NOT depend on `paramixel-engine`.
- Must NOT depend on Maven plugin APIs.
- Must NOT use JUnit Jupiter annotations or Spring annotations.
- Must NOT contain persistence, HTTP, or messaging code.
- Must NOT introduce `@Autowired` or any IoC container annotation.

### Configuration Properties

None. This module has no runtime configuration of its own.

---

## Module: `paramixel-engine`

### Responsibility

Implements the JUnit Platform `TestEngine` SPI: discovers `@TestClass`-annotated classes, validates method signatures, builds a descriptor tree, and executes tests using virtual threads with a concurrency-limited executor.

### Internal Package Structure

```
org.paramixel.engine
├── ParamixelTestEngine.java              TestEngine entry point (discover + execute)
│
├── api/
│   ├── ConcreteEngineContext.java        EngineContext implementation
│   ├── ConcreteClassContext.java         ClassContext implementation + metrics
│   ├── ConcreteArgumentContext.java      ArgumentContext implementation
│   ├── ConcreteArgumentsCollector.java  ArgumentsCollector collector
│   └── ConcreteStore.java               ConcurrentHashMap-backed Store
│
├── descriptor/
│   ├── AbstractParamixelDescriptor.java  Base TestDescriptor
│   ├── ParamixelEngineDescriptor.java    Root (engine-level)
│   ├── ParamixelTestClassDescriptor.java Per test class
│   ├── ParamixelTestArgumentDescriptor.java Per argument bucket
│   ├── ParamixelTestMethodDescriptor.java Per test method
│   └── ParamixelInvocationDescriptor.java (present; used as base for method descriptor)
│
├── discovery/
│   └── ParamixelDiscovery.java          Handles all selector types; builds descriptor tree
│
├── execution/
│   ├── ParamixelExecutionRuntime.java   Virtual-thread executor + concurrency limiter
│   ├── ParamixelConcurrencyLimiter.java Three-semaphore concurrency cap
│   ├── ParamixelClassRunner.java        Manages one test class (Initialize/Finalize/BeforeAll/AfterAll)
│   └── ParamixelInvocationRunner.java   Manages method invocations for one argument bucket
│
├── invoker/
│   └── ParamixelReflectionInvoker.java  Reflective invocation with ITE unwrapping; accessible cache
│
├── listener/
│   ├── AbstractEngineExecutionListener.java  Base listener with ExecutionSummary counters
│   ├── ParamixelEngineExecutionListener.java Dispatching listener (routes by descriptor type)
│   ├── ParamixelEngineDescriptorEngineExecutionListener.java  Engine-level events
│   ├── ParamixelTestClassDescriptorEngineExecutionListener.java  Class-level reporting
│   ├── ParamixelTestArgumentDescriptorEngineExecutionListener.java  Argument-level reporting
│   └── ParamixelTestMethodDescriptorEngineExecutionListener.java  Method-level reporting
│
├── util/
│   └── FastId.java                      Random alphanumeric ID generator (thread-local random)
│
└── validation/
    └── MethodValidator.java             Static validation of all lifecycle method signatures
```

### Layer Breakdown

| Layer | Package | Responsibility |
|---|---|---|
| SPI entry point | `org.paramixel.engine` | `ParamixelTestEngine` — called by JUnit Platform |
| Concrete API impl | `org.paramixel.engine.api` | Concrete implementations of all api interfaces |
| Descriptor model | `org.paramixel.engine.descriptor` | JUnit Platform `TestDescriptor` hierarchy |
| Discovery | `org.paramixel.engine.discovery` | Test class scanner; arguments collector invoker |
| Execution | `org.paramixel.engine.execution` | Runtime, limiter, class runner, invocation runner |
| Invocation | `org.paramixel.engine.invoker` | Reflective method invocation with error unwrapping |
| Reporting | `org.paramixel.engine.listener` | Console output + execution counters |
| Utilities | `org.paramixel.engine.util` | `FastId` (thread-name suffix generation) |
| Validation | `org.paramixel.engine.validation` | Method signature rules enforced at discovery |

### Key Classes and Roles

| Class | Role |
|---|---|
| `ParamixelTestEngine` | JUnit Platform SPI entry; owns discover + execute |
| `ParamixelDiscovery` | Stateless scanner; handles 6 selector types; invokes `@Paramixel.ArgumentsCollector` at discovery time (if multiple exist in the hierarchy, uses the most specific subclass method) |
| `MethodValidator` | Static; validates all lifecycle method signatures; returns `ValidationFailure` list |
| `ConcreteEngineContext` | Immutable; holds config Properties + classParallelism |
| `ConcreteClassContext` | Thread-safe; holds metrics (`AtomicInteger`s), first failure (`AtomicReference`), arg context cache |
| `ConcreteArgumentContext` | Immutable payload holder; has argument-scoped `Store` |
| `ConcreteStore` | `ConcurrentHashMap` backed store; null ≡ remove |
| `ParamixelExecutionRuntime` | Owns virtual-thread executor; `AutoCloseable` with 30s shutdown timeout |
| `ParamixelConcurrencyLimiter` | Fair semaphores: total=`cores*2`, class=`cores`, argument=`cores` |
| `ParamixelClassRunner` | Manages full class lifecycle; holds lifecycle method cache (`ConcurrentHashMap`) |
| `ParamixelInvocationRunner` | Per-argument method execution; supports ordered sequential or parallel modes |
| `ParamixelReflectionInvoker` | Static utility; caches `setAccessible(true)` calls |
| `ParamixelEngineExecutionListener` | Maven-mode listener; dispatches to type-specific sub-listeners; updates `ExecutionSummary` |
| `FastId` | Generates 6-char alphanumeric thread-name suffixes; avoids forbidden words |

### What This Module MUST NOT Do

- Must NOT expose any API classes/interfaces that test authors are expected to import (those live in `paramixel-api`).
- Must NOT depend on Spring, Guice, or any IoC container.
- Must NOT perform file I/O outside loading `paramixel.properties` and scanning classpath.
- Must NOT depend on the `paramixel-maven-plugin` module.
- Must NOT use HTTP clients or messaging clients.
- Must NOT store persistent state between test runs (all state is per-execution in-memory).

### Configuration Properties

| Source | Key | Consumed by |
|---|---|---|
| JUnit Platform config | `invokedBy` | `ParamixelTestEngine.execute()` — selects listener mode |
| JUnit Platform config | `parallelism` | `ParamixelTestEngine.execute()` — sets class parallelism |
| `paramixel.properties` (file) | Any key | Available via `EngineContext.getConfigurationValue()` |
| `paramixel.properties` (built-in resource) | `version` | Engine version string |

---

## Module: `paramixel-maven-plugin`

### Responsibility

Bridges the Maven build lifecycle to the Paramixel engine. Provides a single Maven goal (`test`) that discovers `@TestClass` classes in the test output directory and executes them via the JUnit Platform Launcher.

### Internal Package Structure

```
org.paramixel.maven.plugin
└── ParamixelMojo.java    The only source file; all Maven plugin logic
```

### Layer Breakdown

| Layer | Contents |
|---|---|
| Maven Mojo | `ParamixelMojo` — single class with execute(), discovery, classloader build, execution |

### Key Classes and Roles

| Class | Role |
|---|---|
| `ParamixelMojo` | Extends `AbstractMojo`; goal=`test`; phase=`test`; requiresDependencyResolution=`TEST` |

**Important inner methods:**
- `buildTestClassLoader()` — constructs `URLClassLoader` from test + main output dirs + test classpath elements.
- `discoverTestClasses(ClassLoader)` — scans `.class` files, loads, filters by `@Paramixel.TestClass`.
- `scanForTestClasses(File)` — recursive directory walker; excludes inner classes (`$`).
- `executeTests(List<Class<?>>)` — builds `LauncherDiscoveryRequest`, executes, checks failure count.

### What This Module MUST NOT Do

- Must NOT implement test execution logic (that belongs in `paramixel-engine`).
- Must NOT bypass the JUnit Platform Launcher.
- Must NOT contain test code (`@Paramixel.TestClass` annotated classes).
- Must NOT depend on engine-internal packages (`org.paramixel.engine.*`), only on `paramixel-api` and `paramixel-engine` as opaque dependencies.
- Must NOT introduce HTTP, persistence, or messaging dependencies.

### Configuration Properties

Exposed as Maven Mojo parameters (see spec/03-api-contracts.md §4):
- `skipTests` / `paramixel.failIfNoTests` / `paramixel.class.parallelism` / `paramixel.verbose`

---

## Module: `paramixel-tests`

### Responsibility

Functional/integration test suite that validates the engine's lifecycle behaviour, argument handling, concurrency, store scoping, and ordering features. Tests are authored as `@Paramixel.TestClass` classes and executed by the `paramixel-maven-plugin` goal during the `test` phase. Standard Surefire is **disabled** (`skipTests=true`) for this module.

### Internal Package Structure

```
test/
├── BasicTest.java                    Lifecycle counter assertions (5 args × 2 tests)
├── ClassThreadLocalTest1/2.java      ThreadLocal isolation across argument threads
├── CollectionArgumentsTest.java      ArgumentsCollector using addArguments
├── CollectionMixedArgumentsTest.java Mixed-type collection supplier
├── DisabledTest.java                 @Disabled class behaviour
├── DisplayNameTest.java              @DisplayName propagation
├── IterableArgumentsTest.java        ArgumentsCollector iterating over an Iterable
├── ParallelArgumentTest.java         Parallel argument execution verification
├── SingleObjectArgumentsTest.java    ArgumentsCollector using addArgument
├── StreamArgumentsTest.java          ArgumentsCollector using addArguments
├── argument/                         Detailed per-argument-type tests
│   ├── ArgumentsTest.java
│   ├── ArrayArgumentsTest.java
│   ├── AutoCloseableCustomArgumentTest.java
│   ├── CollectionArgumentsTest.java
│   ├── CustomArgumentTest.java
│   ├── EnumArgumentsTest.java
│   ├── NamedArgumentTest.java
│   ├── NullArgumentTest.java
│   └── ... (20+ files)
├── lifecycle/                        Lifecycle ordering and inheritance tests
│   ├── LifecycleTest.java
│   ├── LifecycleInheritanceTest.java
│   └── ...
├── named/                            Named value display name tests
├── order/                            @Order annotation tests
└── store/                            Store scoping and AutoCloseable tests
    ├── StoreTest1-4.java
    └── StoreAutoCloseableTest.java
```

### Key Classes and Roles

All classes are `@Paramixel.TestClass` test classes. No production code resides here. Notable:

| Class | What it tests |
|---|---|
| `BasicTest` | Core lifecycle method call counts and argument index tracking |
| `ParallelArgumentTest` | Concurrent argument execution |
| `LifecycleInheritanceTest` | Superclass lifecycle method inheritance ordering |
| `StoreTest1` | Engine/class/argument store scoping |
| `StoreAutoCloseableTest` | AutoCloseable value auto-close by engine |
| `OrderAnnotationTest` | `@Order` sequential execution within an argument bucket |
| `NamedArgumentTest` | `Named` display name propagation |
| `NullArgumentTest` | Null argument handling |

### What This Module MUST NOT Do

- Must NOT contain production engine code (no `TestEngine` implementations).
- Must NOT use JUnit Jupiter `@Test` or `@ParameterizedTest`.
- Must NOT depend directly on `org.paramixel.engine.*` implementation classes (only on `paramixel-api` and `paramixel-engine` jar).
- Must NOT add Testcontainers or external service dependencies (those go in `paramixel-examples`).

### Configuration Properties

Inherits Maven plugin configuration from its `pom.xml`:
- `paramixel-maven-plugin` with `verbose=true`, `skip=false`
- Surefire: `skipTests=true`, JUnit auto-detection disabled

---

## Module: `paramixel-examples`

### Responsibility

Demonstrates the Paramixel API with realistic usage patterns. Contains simple sequential and parallel examples, and Testcontainers-based integration tests for Kafka, MongoDB, nginx, and a custom bufstream/tansu service. Executed by the `paramixel-maven-plugin` goal; Surefire is disabled.

### Internal Package Structure

```
examples/
├── simple/
│   ├── ParallelArgumentTest.java     10-argument parallel test with full lifecycle
│   └── SequentialArgumentTest.java   10-argument sequential test with full lifecycle
├── complex/
│   ├── ParallelArgumentTest.java     Complex parallel scenario
│   └── SequentialArgumentTest.java   Complex sequential scenario
├── support/
│   ├── Logger.java                   Printf-style logger wrapper
│   ├── Resource.java                 Sample resource for test lifecycle
│   ├── CleanupExecutor.java          Sequential multi-step cleanup utility
│   ├── TextBlock.java                Text block helper
│   └── ThrowableTask.java            Functional interface for throwing lambdas
└── testcontainers/
    ├── kafka/
    │   ├── KafkaTestEnvironment.java   Wraps Kafka Testcontainer
    │   └── KafkaTest.java             Produce + consume with ordered @Test methods
    ├── mongodb/
    │   ├── MongoDBTestEnvironment.java
    │   └── MongoDBTest.java
    ├── nginx/
    │   ├── NginxTestEnvironment.java
    │   └── NginxTest.java
    ├── bufstream/
    │   ├── BufstreamTestEnvironment.java
    │   └── BufstreamTest.java
    ├── tansu/
    │   ├── TansuTestEnvironment.java
    │   └── TansuTest.java
    └── util/
        ├── CleanupExecutor.java        Cleanup utility specific to testcontainers examples
        ├── ContainerLogConsumer.java   Forwards container logs to test output
        ├── HostPortSocketWaitStrategy.java  Custom wait strategy
        ├── RandomUtil.java             Random string generator for test data
        └── ThrowableTask.java          Functional interface for throwing lambdas
```

### Key Classes and Roles

| Class | Role |
|---|---|
| `examples.simple.ParallelArgumentTest` | Template for parallel argument usage with context-driven supplier |
| `examples.testcontainers.kafka.KafkaTest` | Shows `@Order` + `Store` pattern for stateful produce/consume test |
| `examples.testcontainers.util.CleanupExecutor` | Utility for ordered cleanup with `addTaskIfPresent()` chaining |
| `KafkaTestEnvironment` / `MongoDBTestEnvironment` / etc. | `Named`-implementing test environment objects supplied to `@Paramixel.ArgumentsCollector` |

### What This Module MUST NOT Do

- Must NOT contain unit tests for the engine itself (those go in `paramixel-engine` or `paramixel-tests`).
- Must NOT add dependencies consumed by `paramixel-api` or `paramixel-engine`.
- Must NOT reference engine-internal packages.
- Must NOT have production code that depends on Testcontainers (all Testcontainers usage is `test` scope only).

### Configuration Properties

- `paramixel-maven-plugin` with `verbose=true`, `skip=false`
- `slf4j-nop` bound for test scope (suppresses logging noise during example runs)
- Surefire: `skipTests=true`, JUnit auto-detection disabled
