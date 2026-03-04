# Paramixel — Coding Conventions

---

## 1. Package Naming Pattern

| Module | Root package | Sub-packages |
|---|---|---|
| `paramixel-api` | `org.paramixel.api` | None (flat) |
| `paramixel-engine` | `org.paramixel.engine` | `api`, `descriptor`, `discovery`, `execution`, `filter`, `invoker`, `listener`, `util`, `validation` |
| `paramixel-maven-plugin` | `org.paramixel.maven.plugin` | None |
| `paramixel-tests` | `test` (or `test.<subpackage>`) | `argument`, `lifecycle`, `named`, `order`, `store`, `tags` |
| `paramixel-examples` | `examples` | `simple`, `complex`, `support`, `testcontainers.*`, `testcontainers.util` |
| `paramixel-benchmarks` | `org.paramixel.engine` | `api`, `filter`, `util` |

**Rule:** New engine classes go into the sub-package that matches their layer (e.g., a new runner class → `org.paramixel.engine.execution`). New API types go into `org.paramixel.api`.

**Benchmarks note:** The `paramixel-benchmarks` module intentionally uses the `org.paramixel.engine.*` package namespace
and places sources under `benchmarks/src/main/java` to support JMH discovery/execution.

---

## 2. Class Naming Conventions

| Category | Pattern | Examples |
|---|---|---|
| Public API interfaces | Plain noun | `ArgumentContext`, `ClassContext`, `EngineContext`, `Store`, `Named` |
| Public API concrete value object | Plain noun (or `NamedX`) | `NamedValue` |
| Concrete context impl | `Concrete` prefix | `ConcreteEngineContext`, `ConcreteClassContext`, `ConcreteArgumentContext`, `ConcreteStore` |
| Concrete supplier context | `Concrete` prefix | `ConcreteArgumentsCollector` |
| JUnit descriptor types | `Paramixel` prefix + subject + `Descriptor` | `ParamixelEngineDescriptor`, `ParamixelTestClassDescriptor`, `ParamixelTestArgumentDescriptor`, `ParamixelTestMethodDescriptor` |
| Abstract base descriptor | `Abstract` prefix + `Descriptor` | `AbstractParamixelDescriptor` |
| Discovery | `Paramixel` prefix + `Discovery` | `ParamixelDiscovery` |
| Runners | `Paramixel` prefix + subject + `Runner` | `ParamixelClassRunner`, `ParamixelInvocationRunner` |
| Execution support | `Paramixel` prefix + noun | `ParamixelExecutionRuntime`, `ParamixelConcurrencyLimiter` |
| Invokers | `Paramixel` prefix + `Invoker` | `ParamixelReflectionInvoker` |
| Listeners | `Paramixel` prefix + subject + `Listener` | `ParamixelEngineExecutionListener` |
| Abstract listener base | `Abstract` prefix + noun | `AbstractEngineExecutionListener` |
| Validators | Subject + `Validator` | `MethodValidator` |
| Utilities | Subject + utility purpose | `FastId` |
| Maven Mojo | Subject + `Mojo` | `ParamixelMojo` |
| Test engine entry point | `Paramixel` + `TestEngine` | `ParamixelTestEngine` |
| Example test classes | `<Scenario>Test` | `ParallelArgumentTest`, `KafkaTest` |
| Test environment helpers | Subject + `TestEnvironment` | `KafkaTestEnvironment`, `MongoDBTestEnvironment` |
| Functional test classes (tests module) | `<Feature>Test` or `<Feature>Test<N>` | `BasicTest`, `StoreTest1`, `LifecycleInheritanceTest` |
| Inner cache-key classes | `<Usage>CacheKey` | `LifecycleCacheKey` |
| Inner permit classes | Subject + `Permit` | `ClassPermit`, `ArgumentPermit` |
| Inner holder classes | Subject + `Holder` | `AtomicReferenceHolder` |

**Rule:** Prefer the exact patterns above. Introduce no new suffix patterns without updating this spec.

---

## 3. Dependency Injection Style

**Constructor injection only.** All dependencies are declared as `final` fields and injected through the constructor.

```java
// CORRECT
public final class ParamixelClassRunner {
    private final ParamixelExecutionRuntime runtime;
    private final ConcreteEngineContext engineContext;
    ...
    public ParamixelClassRunner(
            final ParamixelExecutionRuntime runtime,
            final ConcreteEngineContext engineContext,
            ...) {
        this.runtime = runtime;
        this.engineContext = engineContext;
        ...
    }
}
```

**Prohibited:** `@Autowired` on fields, setter injection, `@Inject` on fields, service locator pattern.

There is no IoC container. Object graphs are wired manually in `ParamixelTestEngine.execute()`.

---

## 4. Null Safety

- All public method parameters that must not be null are annotated `@NonNull` from `org.jspecify.annotations`.
- Constructors use `Objects.requireNonNull(param, "param must not be null")` for each non-null parameter.
- Return values that may be null are documented explicitly in Javadoc (`or {@code null} if...`).
- Nullable parameters are documented but not annotated `@Nullable` in the current codebase (only `@NonNull` is used).

```java
// CORRECT
public ConcreteEngineContext(
        final @NonNull String engineId,
        final @NonNull Properties configuration,
        final int classParallelism) {
    this.engineId = Objects.requireNonNull(engineId, "engineId must not be null");
    ...
}
```

---

## 5. Exception Handling Strategy

**There is no global exception handler.** Each execution layer handles exceptions explicitly:

| Scope | Policy |
|---|---|
| `@Paramixel.Initialize` / `@Paramixel.BeforeAll` / `@Paramixel.BeforeEach` failure | Log + abort the current scope; record on `classContext.recordFailure(t)` |
| `@Paramixel.Test` failure | Log + count as test failure; `@Paramixel.AfterEach` still executes |
| `@Paramixel.AfterEach` / `@Paramixel.AfterAll` / `@Paramixel.Finalize` failure | Log + record; execution of remaining hooks continues |
| `AutoCloseable.close()` failure | Log + record; execution continues |
| Reflection (`InvocationTargetException`) | Always unwrap via `e.getCause() != null ? e.getCause() : e` |
| Discovery validation failure | Throws `IllegalStateException` from `ParamixelDiscovery.discoverTestClass()` |

**All exceptions must be unchecked at module boundaries.** `throws Throwable` is acceptable in internal invoker signatures to propagate user exceptions cleanly.

**`@SuppressWarnings` is prohibited** to hide compiler or static analysis warnings.

---

## 6. Logging Convention

| Module | Logger type | Usage |
|---|---|---|
| `paramixel-engine` | `java.util.logging.Logger` via `Logger.getLogger(ClassName.class.getName())` | `FINE` for normal flow, `WARNING` for lifecycle exceptions, `SEVERE` for fatal failures |
| `paramixel-maven-plugin` | `AbstractMojo.getLog()` | `info`, `warn` for Mojo-level events |
| Examples / tests | Custom `examples.support.Logger` (printf-style wrapper) | `info` format strings |

**Engine logger pattern:**
```java
private static final Logger LOGGER = Logger.getLogger(MyClass.class.getName());
// Usage:
LOGGER.fine("Discovery complete. Found " + count + " classes");
LOGGER.log(Level.WARNING, "Lifecycle hook failed", throwable);
LOGGER.log(Level.SEVERE, "Fatal: could not instantiate " + className, e);
```

**Prohibited:** `System.out.println` in engine/api/plugin production code (only in `ParamixelEngineExecutionListener` for deliberate console output in Maven mode). Do not use SLF4J Logger directly — only the SLF4J API is a declared dependency, and it is not the primary logger; JUL is.

---

## 7. DTO / Mapping Strategy

There are no DTOs or mapping frameworks (no MapStruct, no ModelMapper). The project uses:
- Direct field access via constructors.
- The `NamedValue<T>` value object as a general name+payload carrier.
- JUnit Platform `TestExecutionResult` for conveying pass/fail/abort state.

---

## 8. Code Style and Formatting

- **Formatter:** Palantir Java Format (PALANTIR style), version 2.87.0.
- **Applied at:** Every `compile` phase via `spotless-maven-plugin:apply`.
- **License header:** Apache 2.0, sourced from `assets/license-header.txt`. Applied to all Java files.
- **Encoding:** UTF-8 everywhere.

**Never manually format code** — run `./mvnw compile` or `./mvnw spotless:apply` to apply formatting.

Avoid redundant `toString()` calls in string concatenation. For example, prefer
`INFO + " " + durationLine` over `INFO + " " + durationLine.toString()`.

---

## 9. `final` Usage

- All fields that are set in the constructor and never mutated are declared `final`.
- All utility classes (static methods only) are declared `final` with a private no-arg constructor.
- All concrete implementation classes that are not designed for extension are declared `final`.
- Method parameters are declared `final` throughout the engine and plugin source.

---

## 10. Mandatory Rules (MUST always do)

1. **Every public method parameter that must be non-null MUST be annotated `@NonNull`** and validated with `Objects.requireNonNull()` in constructors.
2. **All new Java source files MUST start with the Apache 2.0 license header** from `assets/license-header.txt`.
3. **All new classes MUST be formatted with Palantir Java Format** — commit only after `./mvnw compile` succeeds (Spotless applies automatically).
4. **Every new production class in `paramixel-engine` MUST have a corresponding unit test** in `engine/src/test/java/`.
5. **Constructor injection MUST be used** for all dependencies. No field-level DI annotations.
6. **`final` MUST be used** on all fields set in constructors and on all utility classes.
7. **`@SuppressWarnings` MUST NOT be used in production code** to suppress compiler or static analysis warnings.
   It MAY be used in test sources (`*/src/test/java/**`) when necessary (e.g., unavoidable generic casts in assertions).
8. **New public API types (interfaces, annotations, value objects) MUST live in `org.paramixel.api`** and only in `paramixel-api`.
9. **Engine-internal types MUST live in the appropriate `org.paramixel.engine.<layer>` sub-package**.
10. **Lifecycle exceptions MUST be recorded via `classContext.recordFailure(t)`** and must NOT propagate silently.
11. **Any error message that mentions a Paramixel annotation MUST qualify it as `@Paramixel.<AnnotationName>`** (e.g., `@Paramixel.BeforeAll`, not `@BeforeAll`).

---

## 11. Prohibited Patterns (MUST NEVER do)

1. Never use `@Autowired`, `@Inject`, `@Component`, `@Service`, or any Spring/CDI annotation.
2. Never use field injection (injecting dependencies via annotated fields).
3. Never add `System.out.println` in engine or api production code outside `ParamixelEngineExecutionListener`.
4. Never catch `Throwable` and swallow it without recording via `classContext.recordFailure(t)` or logging at `WARNING` or higher.
5. Never reference engine-internal packages (`org.paramixel.engine.*`) from `paramixel-api`.
6. Never reference `paramixel-maven-plugin` or `paramixel-engine` from `paramixel-api`.
7. Never add `@Paramixel.TestClass`-annotated classes to `paramixel-engine` or `paramixel-maven-plugin` source.
8. Never use JUnit Jupiter annotations (`@org.junit.jupiter.api.Test`, `@BeforeEach`, etc.) in any module — use Paramixel annotations in test classes and standard `org.junit.jupiter.api.*` only in engine unit tests.
9. Never call `method.setAccessible(true)` outside `ParamixelReflectionInvoker` in production code.

   Exceptions:
   - `constructor.setAccessible(true)` is permitted in
     `engine/src/main/java/org/paramixel/engine/execution/ParamixelClassRunner.java` when instantiating the test class
     via its no-arg constructor. This supports test classes that do not declare an explicit constructor (so the compiler
     provides a default no-arg constructor whose access may be package-private).
   - Test sources (`*/src/test/java/**`) MAY use `setAccessible(true)` when required to validate behavior (e.g., to
     inspect or manipulate private state in JUnit unit tests).
10. Never bypass the `ParamixelConcurrencyLimiter` when submitting class or argument tasks.
11. Never modify a `paramixel-api` interface without updating `.specify/specs/system/02-data-model.md` and `.specify/specs/system/03-api-contracts.md`.
12. Never add a new Maven dependency to a module without explicit user approval and updating this spec.

---

## 12. How To: Add a New API Interface

1. Create `org.paramixel.api.MyNewInterface.java` in `api/src/main/java/org/paramixel/api/`.
2. Add Apache license header.
3. Add full Javadoc with `@since 0.0.1` (or current version).
4. Add `@NonNull` to all non-null method parameters.
5. Create a concrete implementation in `engine/src/main/java/org/paramixel/engine/api/ConcreteMyNew.java`.
6. Write a unit test in `engine/src/test/java/org/paramixel/engine/api/ConcreteMyNewTest.java`.
7. Update `.specify/specs/system/02-data-model.md` with the new interface fields/methods.
8. Update `.specify/specs/system/03-api-contracts.md` with method-level contracts.
9. Run `./mvnw verify -pl api,engine` and fix all errors.

---

## 13. How To: Add a New Annotation

1. Add nested `@interface` inside `org.paramixel.api.Paramixel` following the existing pattern.
2. Set `@Retention(RetentionPolicy.RUNTIME)`, `@Documented`, and appropriate `@Target`.
3. Add full Javadoc describing: method signature requirements, execution guarantees, lifecycle ordering.
4. If the annotation applies to test methods, add validation logic to `MethodValidator.validateTestClass()`.
5. Add a corresponding lifecycle hook in `ParamixelClassRunner` or `ParamixelInvocationRunner` if execution behaviour is needed.
6. Add a test class in `paramixel-tests/src/test/java/test/` to verify the new annotation.
7. Update `.specify/specs/system/02-data-model.md` annotation table and `.specify/specs/system/03-api-contracts.md`.
8. Run `./mvnw verify` and fix all errors.

---

## 14. How To: Add a New Engine Execution Feature

1. Identify the correct sub-package (execution, invoker, listener, etc.).
2. Create the class with `final` modifier if it is not designed for extension.
3. Use constructor injection for all dependencies.
4. Declare a JUL `LOGGER = Logger.getLogger(MyClass.class.getName())`.
5. Annotate all public method parameters with `@NonNull` and validate in constructor.
6. Write a unit test in `engine/src/test/java/org/paramixel/engine/<package>/MyClassTest.java`.
7. Wire the new class in `ParamixelTestEngine.execute()` if it is part of the main flow.
8. Run `./mvnw verify -pl engine` and fix all errors.

---

## 15. How To: Add a New `@Paramixel.TestClass` Test (in `paramixel-tests` or `paramixel-examples`)

1. Create a new Java class in the appropriate package under `src/test/java/`.
2. Annotate it `@Paramixel.TestClass`.
3. Add `@Paramixel.ArgumentsCollector` if parameterized; otherwise the engine provides a single null argument.
4. Add `@Paramixel.Test` methods with signature `public void name(ArgumentContext context)`.
5. Add lifecycle methods as needed (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, etc.) with correct signatures.
6. Do NOT annotate the class with JUnit Jupiter annotations.
7. Run `./mvnw test -pl tests` (or `examples`) and verify the test passes.

---

## 16. How To: Add a New Maven Module

1. Create `<moduleName>/pom.xml` with `<parent>` pointing to `paramixel-parent`.
2. Add `<module>moduleName</module>` to the root `pom.xml`.
3. Add Spotless plugin configuration with the license header file path.
4. Add maven-enforcer-plugin if this module needs enforcer rules.
5. Declare only dependencies that the module directly uses.
6. Update `.specify/specs/system/00-overview.md` module inventory table.
7. Update `.specify/specs/system/04-modules.md` with a new module section.
8. Run `./mvnw verify` from root and fix all errors.

---

## 17. Testing Requirements Per Change Type

| Change type | Required tests |
|---|---|
| New API interface | Unit test for concrete implementation in `engine` module |
| New API annotation | Unit test for new annotation in `engine` module + functional test in `paramixel-tests` |
| New engine class | Unit test in `engine/src/test/java/` mirroring the production package |
| New engine execution feature | Unit test + functional test in `paramixel-tests` exercising end-to-end behaviour |
| New lifecycle hook | Functional test in `paramixel-tests/test/lifecycle/` |
| Bug fix | Unit test demonstrating the bug before fix; must pass after fix |
| New example | New class in `paramixel-examples` (no engine unit test required for examples) |
| Maven plugin change | Manual test via `./mvnw test -pl tests` or `./mvnw test -pl examples` |
