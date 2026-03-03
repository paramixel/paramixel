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

### `@Paramixel.TestClass`

- **Target:** `ElementType.TYPE`
- **Effect:** Marks a class as a Paramixel test class. Required for discovery.
- **Constraint:** None on the class itself. The class must have a public or accessible no-arg constructor.

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
- **Effect:** Declares a test method. Discovered by the engine; executed once per argument supplied by `@Paramixel.ArgumentsCollector` (or once with a null argument if no collector exists).
- **Constraint:** Only one method per class hierarchy may be annotated with `@Paramixel.ArgumentsCollector`. If multiple are present, the one in the most specific subclass is used (invoked); others are ignored with a warning.
- **Validation errors (IllegalStateException at discovery):**
  - Not public
  - Not void return
  - Parameter count ≠ 1 or parameter type ≠ `ArgumentsCollector`
  - Not static modifier

### `@Paramixel.Initialize`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ClassContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute once per class, before `@BeforeAll`, ordered using the rules described in "Inheritance (Flattened)".
- **Failure:** Causes class execution to abort; no tests run.

### `@Paramixel.BeforeAll`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — may be static or instance.
- **Execution:** One or more methods may be declared. All discovered methods execute once per argument bucket, before any `@Test` methods for that argument, ordered using the rules described in "Inheritance (Flattened)".
- **Failure:** Aborts that argument bucket; tests for that argument are skipped. Failure recorded on class context.

### `@Paramixel.BeforeEach`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute before every individual `@Test` method invocation, ordered using the rules described in "Inheritance (Flattened)".
- **Failure:** Aborts that specific test invocation; `@AfterEach` still runs. Failure counted.

### `@Paramixel.AfterEach`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute after every `@Test` method invocation (even on test failure), ordered using the rules described in "Inheritance (Flattened)".
- **Failure:** Recorded; execution continues with next method. Does not suppress test failure.

### `@Paramixel.AfterAll`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ArgumentContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute once per argument bucket, after all `@Test` methods, ordered using the rules described in "Inheritance (Flattened)".
- **Failure:** Recorded; remaining `@AfterAll` methods still execute.

### `@Paramixel.Finalize`

- **Target:** `ElementType.METHOD`
- **Validated signature:** `public void methodName(ClassContext context)` — instance method, not static.
- **Execution:** One or more methods may be declared. All discovered methods execute once per class, after `@AfterAll`, ordered using the rules described in "Inheritance (Flattened)". Guaranteed to run if the class was instantiated.
- **Failure:** Recorded; remaining `@Finalize` methods still execute.

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
- **Effect:** Establishes deterministic execution order. Lower values execute first. Methods without `@Order` sort last (effective value = `Integer.MAX_VALUE`).
- **Side effect:** When **any** test method in a class has `@Order`, ALL methods for that argument are executed sequentially (even if argument parallelism > 1).
- **Validation error:** `@Order` on an unsupported method type; `@Order(value <= 0)`.

### `@Paramixel.Tags`

- **Target:** `ElementType.TYPE`
- **Attribute:** `String[] value()` — required, must contain at least one non-null, non-empty tag.
- **Effect:** Categorizes a test class with metadata tags for organization, filtering, and reporting purposes.
- **Constraints:**
  - Can only be applied to classes annotated with `@Paramixel.TestClass`
  - At most one `@Tags` annotation is allowed per class hierarchy
  - Each tag value must be non-null and non-empty (after trimming)
- **Validation errors (IllegalStateException at discovery):**
  - `@Tags` on a class not annotated with `@TestClass`
  - Multiple `@Tags` annotations in the same class hierarchy
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
| `discover(EngineDiscoveryRequest, UniqueId)` | Returns a `ParamixelEngineDescriptor` populated with class/argument/method descriptors. Throws `IllegalStateException` if a `@TestClass` fails method validation. Never returns null. |
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
| `project` | `project` | (injected) | `MavenProject` | The current Maven project |
| `skipTests` | `skipTests` | `false` | `boolean` | Skips all test execution when true |
| `failIfNoTests` | `paramixel.failIfNoTests` | `true` | `boolean` | Fails build if no `@TestClass` annotated class found |
| `classParallelism` | `paramixel.class.parallelism` | `2147483647` | `int` | Maximum concurrent test classes |
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

Full contract documented in spec/02-data-model.md. Key behavioral notes:
- `null` values are NOT stored; `put(key, null)` == `remove(key)`.
- `ClassCastException` thrown for type mismatches on typed get/put/remove methods.
- `computeIfAbsent` supplier may be invoked multiple times under contention.

---

## 6. Error Contract

All error messages that mention a Paramixel annotation MUST use the qualified form (for example,
`@Paramixel.BeforeAll`, not `@BeforeAll`).

### Discovery-time errors

| Condition | Thrown | Where |
|---|---|---|
| `@TestClass` method fails signature validation | `IllegalStateException` in `ParamixelDiscovery.discoverTestClass()` | Discovery phase |
| `@Order(value <= 0)` or `@Order` on unsupported method type | `IllegalStateException` (via validation failure list) | Discovery phase |
| `@Paramixel.ArgumentsCollector` invocation throws | Warning logged; class gets 0 arguments (empty result) | Discovery phase |
| Class cannot be loaded (`ClassNotFoundException`) | Warning logged; class skipped | Discovery phase |

### Execution-time errors

| Condition | Result |
|---|---|
| `@Initialize` throws | Class execution aborts; `@Finalize` still runs; class marked failed |
| `@BeforeAll` throws | Argument bucket skipped; `@AfterAll` still runs; failure recorded |
| `@BeforeEach` throws | Test invocation marked failed; `@AfterEach` still runs |
| `@Test` throws | Test invocation marked failed; `@AfterEach` still runs |
| `@AfterEach` throws | Recorded; execution continues |
| `@AfterAll` throws | Recorded; execution continues |
| `@Finalize` throws | Recorded; execution continues |
| `AutoCloseable.close()` throws | Recorded on class context |

All exceptions are unwrapped from `InvocationTargetException` by `ParamixelReflectionInvoker`.

### Maven plugin errors

| Condition | Thrown |
|---|---|
| Test execution fails (`N > 0` failures) | `MojoFailureException("Tests failed: N of M tests")` |
| Classpath construction fails | `MojoExecutionException("Failed to execute Paramixel tests", cause)` |
| `skipTests=true` | No exception; logs info |
| No `@TestClass` found + `failIfNoTests=true` | `MojoFailureException("No @Paramixel.TestClass annotated classes found")` |

---

## 7. Tag-Based Test Filtering

The engine supports filtering test classes based on `@Paramixel.Tags` annotations using regular expressions.

### Configuration Parameters

| Parameter | Description | Usage |
|---|---|---|
| `paramixel.tags.include` | Comma-separated regex patterns; classes matching ANY pattern are included | System properties, Maven CLI, JUnit Platform config |
| `paramixel.tags.exclude` | Comma-separated regex patterns; classes matching ANY pattern are excluded | System properties, Maven CLI, JUnit Platform config |
| `tags.include` | Comma-separated regex patterns; classes matching ANY pattern are included | Properties file (`paramixel.properties`) |
| `tags.exclude` | Comma-separated regex patterns; classes matching ANY pattern are excluded | Properties file (`paramixel.properties`) |

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
tags.include=integration-.*
tags.exclude=.*slow.*,.*flaky.*
```

**Note:** In the properties file, use `tags.include` and `tags.exclude` (without the `paramixel.` prefix).

### Error Handling

| Condition | Result |
|---|---|
| Invalid regex pattern | Warning logged; pattern skipped; other patterns still applied |
| Empty pattern string | Ignored |
| No matching classes | Discovery completes with 0 test classes |
