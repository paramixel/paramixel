# Paramixel — Architecture

## Architectural Style

Paramixel follows a **layered plugin architecture** anchored to the JUnit Platform SPI. It is not a traditional Spring/Jakarta EE application — there is no HTTP layer, no database, and no IoC container. The system is a **library/framework** composed of:

1. **API layer** (`paramixel-api`) — pure interfaces and annotations consumed by test authors.
2. **Engine layer** (`paramixel-engine`) — discovers, validates, schedules, and executes tests.
3. **Integration layer** (`paramixel-maven-plugin`) — bridges Maven lifecycle to the JUnit Platform Launcher.

The engine plugs into the JUnit Platform via the standard Java SPI (`META-INF/services/org.junit.platform.engine.TestEngine`), meaning it works transparently in any JUnit Platform-compatible toolchain (Maven Surefire with the right configuration, IntelliJ IDEA, etc.).

---

## Component Interaction Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│  Test Author Code                                                   │
│  @Paramixel.TestClass / @Paramixel.Test / @Paramixel.ArgumentsCollector / ... │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ uses
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  paramixel-api                                                      │
│  Paramixel (annotations)  ArgumentContext  ClassContext             │
│  EngineContext  ArgumentsCollector  Store  Named  NamedValue   │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ implements / depends on
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  paramixel-engine                                                   │
│                                                                     │
│  ParamixelTestEngine (JUnit Platform TestEngine SPI)                │
│    │                                                                │
│    ├─ discover() → ParamixelDiscovery                               │
│    │               ├─ scans classpath for @TestClass               │
│    │               ├─ validates method signatures (MethodValidator) │
│    │               ├─ invokes @Paramixel.ArgumentsCollector to get arguments │
│    │               └─ builds descriptor tree:                       │
│    │                  ParamixelEngineDescriptor                     │
│    │                    └─ ParamixelTestClassDescriptor             │
│    │                         └─ ParamixelTestArgumentDescriptor     │
│    │                              └─ ParamixelTestMethodDescriptor  │
│    │                                                                │
│    └─ execute() → ParamixelExecutionRuntime (virtual threads)       │
│                    ├─ ParamixelConcurrencyLimiter (semaphores)      │
│                    ├─ ParamixelClassRunner (per class)              │
│                    │   ├─ @Initialize / @Finalize lifecycle         │
│                    │   ├─ @BeforeAll / @AfterAll per argument       │
│                    │   └─ ParamixelInvocationRunner (per argument)  │
│                    │       ├─ @BeforeEach / @AfterEach              │
│                    │       └─ @Test method invocation               │
│                    └─ ParamixelEngineExecutionListener (reporting)  │
│                                                                     │
│  ConcreteEngineContext → ConcreteClassContext → ConcreteArgumentContext │
│  ConcreteStore (ConcurrentHashMap)                                  │
│  ParamixelReflectionInvoker (reflection + ITE unwrapping)          │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ launched by
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  paramixel-maven-plugin                                             │
│  ParamixelMojo (goal: test, phase: test)                            │
│    ├─ Scans test-classes dir for @TestClass                         │
│    ├─ Builds URLClassLoader from test classpath                     │
│    ├─ Fires JUnit Platform Launcher with EngineFilter=paramixel     │
│    └─ Fails build on any test failure                               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow: Request End-to-End

### Scenario: Maven running tests via `./mvnw test`

1. **Maven lifecycle** reaches the `test` phase. The `paramixel-maven-plugin:test` goal executes.
2. **`ParamixelMojo.execute()`** builds a `URLClassLoader` from test output + test classpath, then scans `.class` files for classes annotated `@Paramixel.TestClass`. For each found class, it adds a `ClassSelector` to a `LauncherDiscoveryRequest` filtered to engine `"paramixel"`.
3. **JUnit Platform Launcher** calls `ParamixelTestEngine.discover()`.
4. **`ParamixelDiscovery.discoverTests()`** processes each selector, checks for `@Paramixel.TestClass`, validates method signatures via `MethodValidator`, and invokes the selected `@Paramixel.ArgumentsCollector` method to enumerate arguments. If multiple `@Paramixel.ArgumentsCollector` methods exist in the class hierarchy, the most specific (subclass) method is used and the others are ignored with a warning. It builds a four-level descriptor tree (engine → class → argument → method).
5. **`ParamixelTestEngine.execute()`** reads the `invokedBy=maven` configuration parameter and installs the custom `ParamixelEngineExecutionListener` for console output. It creates a `ConcreteEngineContext` loading configuration from `paramixel.properties` (project root, if present).
6. **`ParamixelExecutionRuntime`** provides a `newVirtualThreadPerTaskExecutor()` and a `ParamixelConcurrencyLimiter` backed by three fair semaphores (total=`cores*2`, class=`cores`, argument=`cores`).
7. For each `ParamixelTestClassDescriptor`, a class-level permit is acquired and the class is submitted to the virtual-thread executor via `ParamixelClassRunner.runTestClass()`.
8. **`ParamixelClassRunner`** instantiates the test class (no-arg constructor via reflection), runs `@Initialize`, then for each argument:
   - Acquires an optional argument permit.
   - Calls `@BeforeAll`, delegates to `ParamixelInvocationRunner.runInvocations()`, calls `@AfterAll`, and calls `AutoCloseable.close()` on the argument if applicable.
9. **`ParamixelInvocationRunner`** iterates method descriptors, running `@BeforeEach → @Test → @AfterEach` per method. Methods with `@Order` are always sequential; otherwise they may execute concurrently up to the configured argument parallelism.
10. All reflective calls go through **`ParamixelReflectionInvoker`**, which unwraps `InvocationTargetException` so user exceptions propagate cleanly.
11. Results flow back as `TestExecutionResult` to `EngineExecutionListener`. The listener routes each event to a descriptor-type-specific sub-listener for formatted console output.
12. `ParamixelMojo` checks `TestExecutionSummary.getTotalFailureCount()` and throws `MojoFailureException` if > 0.

---

## Key Architectural Decisions

### ADR-1: Descriptor tree built at discovery time

The arguments collector is **invoked during discovery** (not execution). This means argument counts and names are fixed when the engine builds its descriptor tree, allowing IDE integration and report counts to be accurate before execution starts. The tradeoff is that collector code runs eagerly.

### ADR-2: Virtual threads for concurrency

All parallel work is submitted to `Executors.newVirtualThreadPerTaskExecutor()`. This eliminates thread-pool sizing concerns and allows high-concurrency scenarios (many Testcontainers environments) without the overhead of platform threads. A `ParamixelConcurrencyLimiter` with fair semaphores still caps the actual concurrency to avoid resource exhaustion.

### ADR-3: Three-level concurrency cap

- **Total slots = `cores * 2`**: hard ceiling on all concurrent work.
- **Class slots = `cores`**: limits parallel test classes.
- **Argument slots = `cores`**: limits parallel argument buckets within a class.

First argument in each class always runs inline (never dispatched) to guarantee progress even when all slots are saturated.

### ADR-4: Separate context hierarchy (EngineContext → ClassContext → ArgumentContext)

State scoping mirrors test hierarchy. Each scope has its own `Store` (backed by `ConcurrentHashMap`). This allows test classes to share objects across arguments (class store), isolate per-argument resources (argument store), and access global config (engine store).

### ADR-5: AutoCloseable resources auto-closed by engine

If a test class instance or an argument value implements `AutoCloseable`, the engine calls `close()` automatically after the appropriate lifecycle phase, reducing boilerplate in test classes.

### ADR-6: Custom listener only in Maven invocation mode

When `invokedBy=maven`, the engine substitutes its own `ParamixelEngineExecutionListener` for the JUnit Platform's standard listener. This enables custom table-formatted output. In IDE/direct-JUnit-Platform mode, the standard listener is used, ensuring compatibility.

### ADR-7: Flattened method inheritance

Lifecycle hooks and test methods are discovered across the full class hierarchy and treated as a single flattened set per `@Paramixel.TestClass`.

Base/subclass location is irrelevant: methods are flattened across the hierarchy per annotation type. If the same method signature appears multiple times in the hierarchy for the same annotation, only the most specific (subclass) declaration is used. For each annotation type (each lifecycle hook type and `@Paramixel.Test`), the resulting method list is ordered deterministically by `@Paramixel.Order` value ascending (unordered last) and then by method name ascending. Results are cached in a `ConcurrentHashMap<LifecycleCacheKey, List<Method>>` to avoid repeated reflection scans.

---

## Cross-Cutting Concerns

| Concern | Implementation |
|---|---|
| Logging | `java.util.logging` (JUL) via `Logger.getLogger(ClassName.class.getName())` throughout engine. SLF4J API declared but JUL is the primary logger. Console output via `System.out.println` in `ParamixelEngineExecutionListener`. |
| Null safety | `@NonNull` from `org.jspecify` on all public method parameters across api and engine. `Objects.requireNonNull()` guards in constructors. |
| Thread safety | `ConcurrentHashMap` for all shared mutable maps. `AtomicInteger`/`AtomicReference` for counters and first-failure tracking. `Semaphore` for concurrency permits. All public API implementations are either immutable or thread-safe. |
| Code style | Spotless + Palantir Java Format enforced at every compile via `spotless:apply`. License header from `assets/license-header.txt` enforced on all Java files. |
| Exception handling | `InvocationTargetException` is always unwrapped in `ParamixelReflectionInvoker`. Lifecycle exceptions (`@AfterAll`, `@Finalize`, `@AfterEach`) are caught, recorded via `classContext.recordFailure()`, and execution continues (no abort). `@Initialize`/`@BeforeAll`/`@BeforeEach` exceptions abort the current scope. |
| Validation | `MethodValidator.validateTestClass()` checks all lifecycle method signatures at discovery time. Invalid classes throw `IllegalStateException` and are excluded from the test run. |
| Resource management | `ParamixelExecutionRuntime` is `AutoCloseable` (shuts down executor on close). Argument/test-instance `AutoCloseable` resources closed by engine post-lifecycle. |
