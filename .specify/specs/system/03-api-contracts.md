# Paramixel — API Contracts

Paramixel has **no HTTP/REST API, no Kafka topics, and no external messaging**. Its public surface is a Java library/framework API that test authors and build tools consume programmatically.

---

## 1. Public Annotation API (`paramixel-api`)

All annotations are nested inside `org.paramixel.api.Paramixel`. They carry `@Retention(RUNTIME)` and `@Documented`.

### Inheritance (Flattened)

For `@Paramixel.Initialize`, `@Paramixel.BeforeAll`, `@Paramixel.BeforeEach`, `@Paramixel.Test`, `@Paramixel.AfterEach`, `@Paramixel.AfterAll`, and `@Paramixel.Finalize`, methods are discovered across the full class hierarchy and treated as a single flattened set per `@Paramixel.TestClass`.

If the same method signature (name + parameter types) is annotated multiple times in the hierarchy for the same Paramixel annotation, only the most specific (subclass) declaration is used (the superclass declaration is ignored for that annotation).

For each annotation type (each lifecycle hook type and `@Paramixel.Test`), the resulting flattened method list is ordered deterministically:
- Primary: `@Paramixel.Order` value ascending
- Secondary: method name ascending
- Methods without `@Paramixel.Order` execute last (effective value = `Integer.MAX_VALUE`).

**Important:** The `@Paramixel.Order` annotation ordering is applied **per annotation type**. This means:
- All `@Paramixel.BeforeEach` methods are ordered among themselves
- All `@Paramixel.Test` methods are ordered among themselves  
- All `@Paramixel.AfterEach` methods are ordered among themselves
- etc.

The ordering of one annotation type does not affect the ordering of another annotation type. For example, a `@Paramixel.BeforeEach` method with `@Paramixel.Order(1)` will still execute before all `@Paramixel.Test` methods, regardless of their `@Paramixel.Order` values.

### `@Paramixel.TestClass`

- **Target:** `ElementType.TYPE`
- **Effect:** Marks a class as a Paramixel test class. Required for discovery.
- **Constraint:** None on the class itself. The class must have a no-arg constructor that the engine can instantiate.
  If the test class does not declare an explicit constructor, the compiler provides a default no-arg constructor whose
  access may be package-private; the engine may use reflection to make that constructor accessible.

### `@Paramixel.Test`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — instance method, not static.
- **Effect:** Declares a test method. Discovered by the engine; executed once per argument supplied by `@Paramixel.ArgumentsCollector` (or once with a null argument if no collector exists).
- **Inheritance:** Included in the flattened method set (see "Inheritance (Flattened)").
- **Validation errors (IllegalStateException at discovery):**
  - Not public
  - Not void return
  - Parameter count ≠ 1 or parameter type ≠ `ArgumentContext`
  - Static modifier

### `@Paramixel.ArgumentsCollector`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public static void methodName(ArgumentsCollector collector)` — static.
- **Effect:** Supplies test arguments. Discovered by the engine; executed once per test class (before instantiation) to provide arguments for all test methods.
- **Constraint:** **Only ONE method per class hierarchy** may be annotated with `@Paramixel.ArgumentsCollector`. When inheritance is used:
  - The method in the **outermost (most specific) subclass** is invoked
  - Methods in parent classes are **ignored** (not invoked)
- **Example:** If `BaseTest` and `ChildTest extends BaseTest` both have @ArgumentsCollector, only ChildTest's method is invoked.
- **Validation errors (IllegalStateException at discovery):**
  - Not public
  - Not void return
  - Parameter count ≠ 1 or parameter type ≠ `ArgumentsCollector`
  - Not static modifier

### `@Paramixel.Initialize`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ClassContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute once per class, before `@Paramixel.BeforeAll`, ordered using the rules described in "Inheritance (Flattened)". Paired with `@Paramixel.Finalize` — if `@Paramixel.Initialize` executes, `@Paramixel.Finalize` is guaranteed to run.
- **Failure:** Causes class execution to abort; no tests run; `@Paramixel.Finalize` still runs.

### `@Paramixel.BeforeAll`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — may be static or instance.
- **Execution:** One or more methods may be declared. All discovered methods execute once per argument bucket, before any `@Paramixel.Test` methods for that argument, ordered using the rules described in "Inheritance (Flattened)". Paired with `@Paramixel.AfterAll` — if `@Paramixel.BeforeAll` executes, `@Paramixel.AfterAll` is guaranteed to run.
- **Failure:** Aborts that argument bucket; tests for that argument are skipped; paired `@Paramixel.AfterAll` still runs. Failure recorded on class context.

### `@Paramixel.BeforeEach`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute before every individual `@Paramixel.Test` method invocation, ordered using the rules described in "Inheritance (Flattened)". Paired with `@Paramixel.AfterEach` — if `@Paramixel.BeforeEach` executes, `@Paramixel.AfterEach` is guaranteed to run.
- **Failure:** Aborts that specific test invocation; paired `@Paramixel.AfterEach` still runs. Failure counted.

### `@Paramixel.AfterEach`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute after every `@Paramixel.Test` method invocation (even on test failure), ordered using the rules described in "Inheritance (Flattened)". Only executes if `@Paramixel.BeforeEach` was executed for that test method.
- **Failure:** Exception printed to console; remaining tests for THIS argument are aborted; the test class is marked FAILED. Does not suppress execution of paired `@Paramixel.AfterAll` or `@Paramixel.Finalize`.

### `@Paramixel.AfterAll`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute once per argument bucket, after all `@Paramixel.Test` methods, ordered using the rules described in "Inheritance (Flattened)". Only executes if `@Paramixel.BeforeAll` was executed for that argument.
- **Failure:** Exception printed to console; remaining `@Paramixel.AfterAll` methods still execute; paired `@Paramixel.Finalize` still runs; the test class is marked FAILED.

### `@Paramixel.Finalize`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ClassContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute once per class, after `@Paramixel.AfterAll`, ordered using the rules described in "Inheritance (Flattened)". Only executes if `@Paramixel.Initialize` was executed for the class.
- **Failure:** Exception printed to console; remaining `@Paramixel.Finalize` methods still execute; the test class is marked FAILED.

### `@Paramixel.Disabled`

- **Target:** `ElementType.TYPE`, `ElementType.METHOD`
- **Attribute:** `String value()` — optional reason string (default `""`).
- **Effect on class:** Class is skipped entirely during discovery (no methods executed).
- **Effect on method:** Method is excluded from the test method list at discovery.

### `@Paramixel.DisplayName`

- **Target:** `ElementType.TYPE`, `ElementType.METHOD`
- **Attribute:** `String value()` — required, non-empty after trimming.
- **Effect:** Replaces the default display name (class FQN or method name) in reports and listeners.

### `@Paramixel.Order`

- **Target:** `ElementType.METHOD` (valid on `@Paramixel.Test` and on lifecycle hook methods)
- **Attribute:** `int value()` — must be > 0.
- **Effect:** Establishes deterministic execution order. Lower values execute first. Methods without `@Paramixel.Order` sort last (effective value = `Integer.MAX_VALUE`).
- **Side effect:** When **any** test method in a class has `@Paramixel.Order`, ALL methods for that argument are executed sequentially (even if argument parallelism > 1).
- **Validation error:** `@Paramixel.Order` on an unsupported method type; `@Paramixel.Order(value <= 0)`.

### `@Paramixel.Tags`

- **Target:** `ElementType.TYPE`
- **Attribute:** `String[] value()` — required, must contain at least one non-null, non-empty tag.
- **Effect:** Categorizes a test class with metadata tags for organization, filtering, and reporting purposes.
- **Constraints:**
  - Can only be applied to classes annotated with `@Paramixel.TestClass`
  - At most one `@Paramixel.Tags` annotation is allowed per class (each class in a hierarchy can have its own `@Paramixel.Tags`)
  - Each tag value must be non-null and non-empty (after trimming)
- **Inheritance Behavior:**
  - Tags are inherited from parent classes and combined with the current class's tags
  - When filtering, a class matches if ANY of its tags (inherited or declared) match the pattern
  - Example: If `BaseTest` has `@Paramixel.Tags("integration")` and `ChildTest extends BaseTest` has `@Paramixel.Tags("fast")`, then `ChildTest` has both tags: `["integration", "fast"]`
- **Validation errors (IllegalStateException at discovery):**
  - `@Paramixel.Tags` on a class not annotated with `@Paramixel.TestClass`
  - Multiple `@Paramixel.Tags` annotations on the same class
  - Empty tags array
  - Tags array containing null elements
  - Tags array containing only empty/blank strings
  - Any tag value that is null or empty/blank

---

## 2. Context API — Method Contracts

### `ArgumentContext.getArgument(Class<T> type)`

```
Contract:
  - If getArgument() == null: returns null (no cast attempted).
  - If getArgument() instanceof type: returns type.cast(getArgument()).
  - Otherwise: throws ClassCastException.
  - Throws NullPointerException if type == null.
```

### `Store.put(String key, Object value)`

```
Contract:
  - Stores value under key.
  - If value == null: equivalent to remove(key); returns previous value.
  - Throws NullPointerException if key == null.
  - Returns the previous value, or null if none.
```

### `Store.computeIfAbsent(String key, Supplier<?> supplier)`

```
Contract:
  - If key already mapped: returns existing value.
  - If key absent: invokes supplier and stores the result (unless supplier returns null).
  - Supplier may be called more than once under contention (ConcurrentHashMap semantics).
  - Throws NullPointerException if key or supplier == null.
```

### `EngineContext.getConfigurationValue(String key, String defaultValue)`

```
Contract:
  - Returns configuration.getProperty(key, defaultValue).
  - Throws NullPointerException if key == null.
  - defaultValue may be null; if key absent, null is returned in that case.
```

---

## 3. JUnit Platform Engine SPI

`ParamixelTestEngine` is registered via `META-INF/services/org.junit.platform.engine.TestEngine` and implements `org.junit.platform.engine.TestEngine`.

| Method | Contract |
|---|---|
| `getId()` | Returns `"paramixel"` — never null, never changes. |
| `discover(EngineDiscoveryRequest, UniqueId)` | Returns a `ParamixelEngineDescriptor` populated with class/argument/method descriptors. Throws `IllegalStateException` if a `@Paramixel.TestClass` fails method validation. Never returns null. |
| `execute(ExecutionRequest)` | Runs all discovered tests. Reports start/finish on the engine descriptor. Never throws. If any test fails, the engine descriptor MUST be finished with `TestExecutionResult.failed(...)` so build tools (including Maven) observe an overall failure. |

---

## 4. Maven Plugin API

### Mojo: `org.paramixel:paramixel-maven-plugin:test`

- **Goal name:** `test`
- **Default phase:** `test`
- **Requires:** project present, test dependency resolution.

#### Configuration Parameters

| Parameter | Property | Default | Type | Description |
|---|---|---|---|---|
| `project` | (injected) | (injected) | `MavenProject` | The current Maven project |
| `skipTests` | `paramixel.skipTests` | `false` | `boolean` | Skips all test execution when true |
| `failIfNoTests` | `paramixel.failIfNoTests` | `true` | `boolean` | Fails build if no `@Paramixel.TestClass` annotated class found |
| `parallelism` | `paramixel.parallelism` | (engine default) | `Integer` | Global maximum parallelism (max concurrent test classes); when unset, engine default applies |
| `verbose` | `paramixel.verbose` | `false` | `boolean` | Enables verbose output (partially implemented) |

#### Behaviour

1. Skips if `skipTests=true` → logs "Tests are skipped."
2. Builds `URLClassLoader` from: test-classes dir → classes dir → all test classpath elements.
3. Scans test-classes dir recursively for `.class` files (excluding inner classes via `$`).
4. Loads each class; keeps those annotated `@Paramixel.TestClass`.
5. If no classes found: fails with `MojoFailureException` (if `failIfNoTests`) or warns.
6. Creates `LauncherDiscoveryRequest` with one `ClassSelector` per test class, filtered to engine `"paramixel"`, with `configurationParameter("invokedBy", "maven")`.
7. Fires `Launcher.execute()`.
8. Throws `MojoFailureException("Tests failed: N of M tests")` if `summary.getTotalFailureCount() > 0`.

In Maven invocation mode (`invokedBy=maven`), the engine also prints a final line containing either `TESTS PASSED` or
`TESTS FAILED`.

---

## 5. Shared Library Interfaces — Method-Level Contracts

### `org.paramixel.api.Named`

```java
String getName();
// Contract: must return non-null String.
// Used by discovery to set argument display name in descriptor.
```

### `org.paramixel.api.Store`

Full contract documented in `.specify/specs/system/02-data-model.md`. Key behavioral notes:
- `null` values are NOT stored; `put(key, null)` == `remove(key)`.
- `ClassCastException` thrown for type mismatches on typed get/put/remove methods.
- `computeIfAbsent` supplier may be invoked multiple times under contention.

---

## 6. Error Contract

All error messages that mention a Paramixel annotation MUST use the qualified form (for example,
`@Paramixel.BeforeAll`, not `@BeforeAll`).

### Discovery-time errors

**Fail-Fast Behavior:** The engine fails immediately on the first validation error. Exceptions are printed to console output and the test run aborts.

| Condition | Result |
|---|---|
| `@Paramixel.TestClass` method fails signature validation | `IllegalStateException` printed to console; test execution aborts |
| `@Paramixel.Order(value <= 0)` or `@Paramixel.Order` on unsupported method type | `IllegalStateException` printed to console; test execution aborts |
| `@Paramixel.ArgumentsCollector` invocation throws | Exception printed to console; test execution aborts |
| Class cannot be loaded (`ClassNotFoundException`) | Exception printed to console; test execution aborts |

**Error Message Format:** All validation errors include the class name, validation failure details, and use qualified annotation names (e.g., `@Paramixel.BeforeAll`).

### Execution-time errors

All exceptions from test engine annotations are **printed to console output** for visibility.

| Condition | Result |
|---|---|
| Test class instantiation fails (no-arg constructor) | All test methods marked FAILED; lifecycle hooks (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, etc.) not executed; exception printed |
| `@Paramixel.Initialize` throws | Class execution aborts; `@Paramixel.Finalize` runs (if @Paramixel.Initialize executed); class marked FAILED |
| `@Paramixel.BeforeAll` throws | Argument bucket skipped; `@Paramixel.AfterAll` runs (if @Paramixel.BeforeAll executed); failure recorded |
| `@Paramixel.BeforeEach` throws | Test invocation marked FAILED; `@Paramixel.AfterEach` runs (if @Paramixel.BeforeEach executed) |
| `@Paramixel.Test` throws | Test invocation marked FAILED; `@Paramixel.AfterEach` runs (if @Paramixel.BeforeEach executed) |
| `@Paramixel.AfterEach` throws | Exception printed; remaining tests for THIS argument aborted; `@Paramixel.AfterAll` runs (if @Paramixel.BeforeAll executed); class marked FAILED |
| `@Paramixel.AfterAll` throws | Exception printed; `@Paramixel.Finalize` runs (if @Paramixel.Initialize executed); class marked FAILED |
| `@Paramixel.Finalize` throws | Exception printed; class marked FAILED |
| `AutoCloseable.close()` throws | Exception printed; class marked FAILED |

**Key Principles:**
1. **Console Visibility**: All exceptions are printed to console output
2. **Lifecycle Pairing**: "After" hooks only run if their paired "before" hook executed (@Paramixel.AfterEach↔@Paramixel.BeforeEach, @Paramixel.AfterAll↔@Paramixel.BeforeAll, @Paramixel.Finalize↔@Paramixel.Initialize)
3. **Fail Fast**: `@Paramixel.AfterEach` failure stops further testing of the current argument
4. **Guaranteed Cleanup**: Paired "after" hooks always execute despite failures
5. **First Failure Wins**: The first recorded exception determines the test class result
6. **Exception Unwrapping**: All exceptions are unwrapped from `InvocationTargetException` by `ParamixelReflectionInvoker`

### Maven plugin errors

| Condition | Thrown |
|---|---|
| Test execution fails (`N > 0` failures) | `MojoFailureException("Tests failed: N of M tests")` |
| Classpath construction fails | `MojoExecutionException("Failed to execute Paramixel tests", cause)` |
| `skipTests=true` | No exception; logs info |
| No `@Paramixel.TestClass` found + `failIfNoTests=true` | `MojoFailureException("No @Paramixel.TestClass annotated classes found")` |

---

## 7. Tag-Based Test Filtering

The engine supports filtering test classes based on `@Paramixel.Tags` annotations using regular expressions.

### Configuration Parameters

| Parameter | Description |
|---|---|
| `paramixel.tags.include` | Comma-separated regex patterns; classes matching ANY pattern are included |
| `paramixel.tags.exclude` | Comma-separated regex patterns; classes matching ANY pattern are excluded |

**Configuration Sources:**
- **System properties**: `-Dparamixel.tags.include=pattern`
- **Maven CLI**: `-Dparamixel.tags.include=pattern`
- **JUnit Platform config**: Via configuration parameters
- **Properties file** (`paramixel.properties`): `paramixel.tags.include=pattern`

### Matching Behavior

1. **Include Patterns Applied First**: A class matches if ANY of its tags matches ANY include pattern
2. **Exclude Patterns Applied Second**: Matching classes are removed if ANY of their tags matches ANY exclude pattern
3. **Default Behavior**: Without include patterns, all classes pass (except excluded ones)
4. **Untagged Classes**: Only included when no include patterns are configured
5. **Case Sensitive**: Regex matching uses Java's default case-sensitive behavior

### Examples

```bash
# Include only integration tests
./mvnw test -Dparamixel.tags.include="integration-.*"

# Exclude slow tests
./mvnw test -Dparamixel.tags.exclude=".*slow.*"

# Include integration tests except slow ones
./mvnw test -Dparamixel.tags.include="integration-.*" -Dparamixel.tags.exclude=".*slow.*"

# Include multiple patterns
./mvnw test -Dparamixel.tags.include="^unit$,^fast$"
```

### Maven Plugin Configuration

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <configuration>
        <includeTags>integration-.*</includeTags>
        <excludeTags>.*-slow</excludeTags>
    </configuration>
</plugin>
```

### Properties File Configuration

Create `paramixel.properties` in the project root:

```properties
paramixel.tags.include=integration-.*
paramixel.tags.exclude=.*slow.*,.*flaky.*
```

### Error Handling

| Condition | Result |
|---|---|
| Invalid regex pattern | Exception printed; test execution fails before discovery |
| Empty pattern string | Ignored |
| No matching classes | Discovery completes with 0 test classes |

**Strict Validation:** Invalid regex patterns in `paramixel.tags.include` or `paramixel.tags.exclude` cause immediate test execution failure. The engine validates all patterns during initialization, before test discovery begins. This ensures configuration errors are caught early and prevents partial test runs with unintended filtering.
