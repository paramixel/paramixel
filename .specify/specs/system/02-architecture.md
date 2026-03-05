# Paramixel -- Architecture

## Architectural Style

Paramixel follows a layered plugin architecture anchored to the JUnit Platform SPI.
There is no HTTP layer, no database, and no IoC container. The system is a
library/framework composed of three layers:

1. **API layer** (`paramixel-api`) -- pure interfaces and annotations consumed by test authors.
2. **Engine layer** (`paramixel-engine`) -- discovers, validates, schedules, and executes tests.
3. **Integration layer** (`paramixel-maven-plugin`) -- bridges Maven lifecycle to JUnit Platform Launcher.

The engine plugs into the JUnit Platform via the standard Java SPI
(`META-INF/services/org.junit.platform.engine.TestEngine`), meaning it works transparently
in any JUnit Platform-compatible toolchain (Maven Surefire with the right configuration,
IntelliJ IDEA, etc.).

## Component Interaction Diagram

```
+-----------------------------------------------------------------------+
|  Test Author Code                                                      |
|  @Paramixel.TestClass / @Paramixel.Test / @Paramixel.ArgumentsCollector / ... |
+------------------------------+----------------------------------------+
                               | uses
                               v
+-----------------------------------------------------------------------+
|  paramixel-api                                                         |
|  Paramixel (annotations)  ArgumentContext  ClassContext                 |
|  EngineContext  ArgumentsCollector  Store  Named  NamedValue           |
+------------------------------+----------------------------------------+
                               | implements / depends on
                               v
+-----------------------------------------------------------------------+
|  paramixel-engine                                                      |
|                                                                        |
|  ParamixelTestEngine (JUnit Platform TestEngine SPI)                   |
|    |                                                                   |
|    +- discover() -> ParamixelDiscovery                                 |
|    |                +- scans classpath for @Paramixel.TestClass         |
|    |                +- validates method signatures (MethodValidator)    |
|    |                +- invokes @Paramixel.ArgumentsCollector            |
|    |                +- builds descriptor tree:                          |
|    |                   ParamixelEngineDescriptor                        |
|    |                     +- ParamixelTestClassDescriptor                |
|    |                          +- ParamixelTestArgumentDescriptor        |
|    |                               +- ParamixelTestMethodDescriptor     |
|    |                                                                   |
|    +- execute() -> ParamixelExecutionRuntime (virtual threads)          |
|                    +- ParamixelConcurrencyLimiter (semaphores)          |
|                    +- ParamixelClassRunner (per class)                  |
|                    |   +- @Paramixel.Initialize / @Paramixel.Finalize  |
|                    |   +- @Paramixel.BeforeAll / @Paramixel.AfterAll   |
|                    |   +- ParamixelInvocationRunner (per argument)      |
|                    |       +- @Paramixel.BeforeEach / @Paramixel.AfterEach |
|                    |       +- @Paramixel.Test method invocation         |
|                    +- ParamixelEngineExecutionListener (reporting)      |
|                                                                        |
|  ConcreteEngineContext -> ConcreteClassContext -> ConcreteArgumentContext|
|  ConcreteStore (ConcurrentHashMap)                                     |
|  ParamixelReflectionInvoker (reflection + ITE unwrapping)              |
+------------------------------+----------------------------------------+
                               | launched by
                               v
+-----------------------------------------------------------------------+
|  paramixel-maven-plugin                                                |
|  ParamixelMojo (goal: test, phase: test)                               |
|    +- Scans test-classes dir for @Paramixel.TestClass                  |
|    +- Builds URLClassLoader from test classpath                        |
|    +- Fires JUnit Platform Launcher with EngineFilter=paramixel        |
|    +- Fails build on any test failure                                  |
+-----------------------------------------------------------------------+
```

## Data Flow: Maven Running Tests

1. **Maven lifecycle** reaches the `test` phase. The `paramixel-maven-plugin:test` goal executes.
2. **`ParamixelMojo.execute()`** builds a `URLClassLoader` from test output + test classpath, then scans `.class` files for classes annotated `@Paramixel.TestClass`. For each found class, it adds a `ClassSelector` to a `LauncherDiscoveryRequest` filtered to engine `"paramixel"`.
3. **JUnit Platform Launcher** calls `ParamixelTestEngine.discover()`.
4. **`ParamixelDiscovery.discoverTests()`** processes each selector, validates method signatures via `MethodValidator`, and invokes the `@Paramixel.ArgumentsCollector` method to enumerate arguments. It builds the four-level descriptor tree (engine -> class -> argument -> method). See `04-lifecycle.md` for argument collector rules and inheritance behavior.
5. **`ParamixelTestEngine.execute()`** reads the `invokedBy=maven` configuration parameter and installs the custom `ParamixelEngineExecutionListener` for console output. It creates a `ConcreteEngineContext` loading configuration from `paramixel.properties`.
6. **`ParamixelExecutionRuntime`** provides a virtual-thread executor and a `ParamixelConcurrencyLimiter`. See `05-concurrency.md` for semaphore configuration.
7. For each `ParamixelTestClassDescriptor`, a class-level permit is acquired and the class is submitted to the virtual-thread executor via `ParamixelClassRunner.runTestClass()`.
8. **`ParamixelClassRunner`** instantiates the test class (no-arg constructor), runs `@Paramixel.Initialize`, then for each argument acquires an argument permit and delegates to `ParamixelInvocationRunner`. See `04-lifecycle.md` for the full execution sequence.
9. All reflective calls go through **`ParamixelReflectionInvoker`**, which unwraps `InvocationTargetException`.
10. Results flow back as `TestExecutionResult` to `EngineExecutionListener`.
11. `ParamixelMojo` checks `TestExecutionSummary.getTotalFailureCount()` and throws `MojoFailureException` if > 0.

## Architectural Decisions

### ADR-1: Descriptor Tree Built at Discovery Time

The arguments collector is invoked during `discover()`, not `execute()`. Argument counts and
names are fixed when the engine builds its descriptor tree. This allows IDE integration and
report counts to be accurate before execution starts. The tradeoff is that collector code
runs eagerly.

### ADR-2: Virtual Threads for All Concurrency

All parallel work MUST be submitted to `Executors.newVirtualThreadPerTaskExecutor()`. Platform
thread pools MUST NOT be introduced. Virtual threads eliminate thread-pool sizing concerns and
support high-concurrency scenarios without platform thread overhead.

### ADR-3: Three-Level Concurrency Cap

See `05-concurrency.md` for the full concurrency model. The engine uses three fair semaphores
to cap concurrency at total, class, and argument levels.

### ADR-4: Separate Context Hierarchy

State scoping mirrors the test hierarchy: `EngineContext -> ClassContext -> ArgumentContext`.
Each scope has its own `Store` backed by `ConcurrentHashMap`. See `03-domain-model.md` for
interface definitions.

### ADR-5: AutoCloseable Resources Auto-Closed

If a test class instance or an argument value implements `AutoCloseable`, the engine MUST call
`close()` automatically after the appropriate lifecycle phase. See `04-lifecycle.md` for
ordering.

### ADR-6: Custom Listener Only in Maven Invocation Mode

When `invokedBy=maven`, the engine substitutes its own `ParamixelEngineExecutionListener`
for the JUnit Platform's standard listener. In IDE/direct-JUnit-Platform mode, the standard
listener is used.

### ADR-7: Flattened Method Inheritance

Lifecycle hooks and test methods are discovered across the full class hierarchy and treated
as a single flattened set. See `04-lifecycle.md` for the complete inheritance and ordering rules.

## Cross-Cutting Concerns

| Concern | Implementation |
|---|---|
| Logging | `java.util.logging` (JUL) via `Logger.getLogger(ClassName.class.getName())` throughout engine. Console output via `System.out.println` in `ParamixelEngineExecutionListener` only. |
| Null safety | `@NonNull` from `org.jspecify` on all public method reference-type parameters. `Objects.requireNonNull()` guards in constructors. |
| Thread safety | `ConcurrentHashMap` for shared mutable maps. `AtomicInteger`/`AtomicReference` for counters and first-failure tracking. `Semaphore` for concurrency permits. |
| Code style | Spotless + Palantir Java Format enforced at every compile. License header from `assets/license-header.txt` on all Java files. |
| Exception handling | See `08-error-handling.md` for the complete error contract. |
| Validation | `MethodValidator.validateTestClass()` checks all lifecycle method signatures at discovery time. |
| Resource management | `ParamixelExecutionRuntime` is `AutoCloseable`. Argument/test-instance `AutoCloseable` resources closed by engine post-lifecycle. |
