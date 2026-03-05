# Paramixel — Data Model

Paramixel is a test framework, not a data-persistence application. There are no JPA entities, no databases, and no ORM mappings. The "data model" consists of the **public API interfaces**, **concrete context implementations**, **JUnit Platform descriptor types**, and **value objects** that carry state through the test execution pipeline.

---

## Public API Interfaces (paramixel-api)

### `ArgumentContext` — `org.paramixel.api`

Represents context for a single argument invocation (one argument value × one test class instantiation). Injected into `@Paramixel.BeforeAll`, `@Paramixel.BeforeEach`, `@Paramixel.Test`, `@Paramixel.AfterEach`, `@Paramixel.AfterAll` methods.

**Context Hierarchy Access:** From an ArgumentContext, access the parent ClassContext via `getClassContext()`, and the root EngineContext via `getClassContext().getEngineContext()`. This allows access to all methods and stores in the context tree.

| Member | Type | Nullable | Description |
|---|---|---|---|
| `getClassContext()` | `ClassContext` | no | Parent class-level context |
| `getStore()` | `Store` | no | Argument-scoped key-value store |
| `getArgument()` | `Object` | yes | The argument value for this invocation; null if no arguments collector |
| `getArgument(Class<T>)` | `T` | yes | Type-safe cast of `getArgument()` |
| `getArgumentIndex()` | `int` | no | Zero-based position of this argument in collector output |

---

### `ArgumentsCollector` — `org.paramixel.api`

Collector passed to a context-driven `@Paramixel.ArgumentsCollector` method (signature: `public static void arguments(ArgumentsCollector collector)`). Allows programmatic argument registration and per-class parallelism override.

| Member | Type | Nullable | Description |
|---|---|---|---|
| `getEngineContext()` | `EngineContext` | no | Parent engine context |
| `addArgument(Object)` | `ArgumentsCollector` | — | Registers one argument value; returns `this` |
| `addArguments(Object...)` | `ArgumentsCollector` | — | Registers multiple arguments; returns `this` |
| `addArguments(List<?>)` | `ArgumentsCollector` | — | Registers list of arguments; returns `this` |
| `setParallelism(int)` | `ArgumentsCollector` | — | Sets per-class argument parallelism (≥ 1); returns `this` |

---

### `ClassContext` — `org.paramixel.api`

Represents context for one test class. Injected into `@Paramixel.Initialize` and `@Paramixel.Finalize` methods. Available via `ArgumentContext.getClassContext()`.

**Context Hierarchy Access:** From a ClassContext, access the root EngineContext via `getEngineContext()`. This allows access to all methods and stores in the context tree.

| Member | Type | Nullable | Description |
|---|---|---|---|
| `getEngineContext()` | `EngineContext` | no | Parent engine context |
| `getStore()` | `Store` | no | Class-scoped key-value store (shared across all arguments for the class) |
| `getTestClass()` | `Class<?>` | no | The `Class` object for the test class |
| `getTestInstance()` | `Object` | yes | The instantiated test object (null before instantiation) |

---

### `EngineContext` — `org.paramixel.api`

Root context, shared across all test classes and arguments for one engine run. Available via `ClassContext.getEngineContext()`.

| Member | Type | Nullable | Description |
|---|---|---|---|
| `getEngineId()` | `String` | no | Always `"paramixel"` |
| `getConfiguration()` | `Properties` | no | Defensive copy of engine configuration (from `paramixel.properties` + runtime overrides) |
| `getConfigurationValue(String)` | `String` | yes | Value for key; null if absent |
| `getConfigurationValue(String, String)` | `String` | yes | Value for key; defaultValue if absent |
| `getStore()` | `Store` | no | Engine-scoped key-value store (shared across all classes) |

---

### `Store` — `org.paramixel.api`

Generic key-value store with typed access and `computeIfAbsent` semantics. Thread-safe per implementation contract.

| Member | Return type | Description |
|---|---|---|
| `put(String, Object)` | `Object` | Stores value; returns previous (null ≡ removes) |
| `put(String, Class<T>, T)` | `T` | Typed put with previous-value type check |
| `get(String)` | `Object` | Raw get; null if absent |
| `get(String, Class<T>)` | `T` | Typed get with ClassCastException on type mismatch |
| `contains(String)` | `boolean` | Presence check |
| `remove(String)` | `Object` | Removes and returns previous |
| `remove(String, Class<T>)` | `T` | Typed remove |
| `clear()` | `void` | Removes all entries |
| `size()` | `int` | Entry count |
| `computeIfAbsent(String, Supplier<?>)` | `Object` | Compute-if-absent; supplier may be invoked multiple times under contention |
| `computeIfAbsent(String, Class<T>, Supplier<T>)` | `T` | Typed compute-if-absent |
| `find(String, Class<T>)` | `Optional<T>` | Optional-returning typed get |
| `keyIterator()` | `Iterator<String>` | Iterates over keys (order unspecified) |

---

### `Named` — `org.paramixel.api`

Optional interface for argument objects that want a custom display name in test reports. If an argument implements `Named`, its `getName()` result becomes the argument display name in the descriptor tree.

| Member | Return | Nullable | Description |
|---|---|---|---|
| `getName()` | `String` | no | Display name; must be non-null |

---

### `NamedValue<T>` — `org.paramixel.api`

Concrete generic value object pairing a name with an arbitrary payload. Implements `Named`.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `name` | `String` | no | Display name (`@NonNull`) |
| `value` | `T` | yes | Wrapped payload |

**Factory method:** `NamedValue.of(String name, T value)` — static, returns new instance.
**Accessors:** `getName()`, `getValue()`, `getValue(Class<V>)` (typed cast).

---

## Annotations (all nested in `Paramixel` class — `org.paramixel.api`)

All annotations have `@Retention(RetentionPolicy.RUNTIME)` and `@Documented`.

| Annotation | Target | Required method signature | Description |
|---|---|---|---|
| `@Paramixel.TestClass` | `TYPE` | N/A (class-level) | Marks a class for discovery |
| `@Paramixel.Test` | `METHOD` | `public void name(ArgumentContext)` — instance, not static | Marks a test method |
| `@Paramixel.ArgumentsCollector` | `METHOD` | `public static void name(ArgumentsCollector)` | Supplies test arguments |
| `@Paramixel.Initialize` | `METHOD` | `public void name(ClassContext)` — instance, not static | Once per class, before all lifecycle |
| `@Paramixel.BeforeAll` | `METHOD` | `public void name(ArgumentContext)` — may be static | Once per argument, before all tests |
| `@Paramixel.BeforeEach` | `METHOD` | `public void name(ArgumentContext)` — instance, not static | Before each test method invocation |
| `@Paramixel.AfterEach` | `METHOD` | `public void name(ArgumentContext)` — instance, not static | After each test method invocation |
| `@Paramixel.AfterAll` | `METHOD` | `public void name(ArgumentContext)` — may be static | Once per argument, after all tests |
| `@Paramixel.Finalize` | `METHOD` | `public void name(ClassContext)` — instance, not static | Once per class, after all lifecycle (guaranteed) |
| `@Paramixel.Disabled` | `TYPE`, `METHOD` | N/A | Skips the annotated class or method; optional `value()` for reason |
| `@Paramixel.DisplayName` | `TYPE`, `METHOD` | N/A | Overrides display name; required `value()` |
| `@Paramixel.Order` | `METHOD` (valid on `@Paramixel.Test` and lifecycle hook methods) | N/A | Explicit ordering; `value()` must be > 0; lower = earlier |
| `@Paramixel.Tags` | `TYPE` | N/A | Categorizes test class with tags; only on `@Paramixel.TestClass` classes; max one per class |

---

## Engine-Internal Types (paramixel-engine)

### Concrete Context Implementations

#### `ConcreteEngineContext` — `org.paramixel.engine.api`

| Field | Type | Notes |
|---|---|---|
| `engineId` | `String` | Always `"paramixel"` |
| `configuration` | `Properties` | Defensive copy; loaded from `paramixel.properties` + runtime overrides (`invokedBy`, `paramixel.parallelism`) |
| `classParallelism` | `int` | Max concurrent test classes; defaults to `availableProcessors` |
| `store` | `Store` | `ConcreteStore` instance |

#### `ConcreteClassContext` — `org.paramixel.engine.api`

| Field | Type | Notes |
|---|---|---|
| `testClass` | `Class<?>` | Not null |
| `engineContext` | `EngineContext` | Not null |
| `testInstance` | `Object` | Nullable (null before instantiation) |
| `invocationCount` | `AtomicInteger` | Total invocations executed |
| `successCount` | `AtomicInteger` | Successful invocations |
| `failureCount` | `AtomicInteger` | Failed invocations |
| `firstFailure` | `AtomicReference<Throwable>` | Records first failure only |
| `store` | `Store` | Class-scoped `ConcreteStore` |
| `argumentContexts` | `ConcurrentHashMap<Integer, ConcreteArgumentContext>` | Cached per-argument contexts; removed after `@Paramixel.AfterAll` |

#### `ConcreteArgumentContext` — `org.paramixel.engine.api`

| Field | Type | Notes |
|---|---|---|
| `classContext` | `ConcreteClassContext` | Not null |
| `argument` | `Object` | Nullable |
| `argumentIndex` | `int` | Zero-based |
| `store` | `Store` | Argument-scoped `ConcreteStore` |

#### `ConcreteArgumentsCollector` — `org.paramixel.engine.api`

| Field | Type | Notes |
|---|---|---|
| `engineContext` | `EngineContext` | Not null |
| `arguments` | `List<Object>` | Ordered insertion list |
| `parallelism` | `int` | Defaults to `max(1, availableProcessors)`; settable via `setParallelism()`. Effective value constrained by global `paramixel.parallelism` setting |

#### `ConcreteStore` — `org.paramixel.engine.api`

Backed by `ConcurrentHashMap<String, Object>`. Null values cause removal (not storage). All methods null-safe via `Objects.requireNonNull` on keys.

---

### Descriptor Types (paramixel-engine)

The JUnit Platform descriptor hierarchy:

```
ParamixelEngineDescriptor        (Type.CONTAINER)
  └─ ParamixelTestClassDescriptor  (Type.CONTAINER)
       └─ ParamixelTestArgumentDescriptor  (Type.CONTAINER)
            └─ ParamixelTestMethodDescriptor  (Type.TEST)
```

#### `AbstractParamixelDescriptor` — `org.paramixel.engine.descriptor`

Base class implementing `org.junit.platform.engine.TestDescriptor`.

| Field | Type | Notes |
|---|---|---|
| `uniqueId` | `UniqueId` | Segment format: `[engine:paramixel]/[class:<fqcn>]/[argument:<idx>]/[method:<name>]` |
| `displayName` | `String` | From `@Paramixel.DisplayName` value or defaults to class/method name |
| `type` | `Type` | `CONTAINER` or `TEST` |
| `parent` | `TestDescriptor` | Nullable |
| `children` | `List<TestDescriptor>` | Ordered |

#### `ParamixelTestClassDescriptor` — extends `AbstractParamixelDescriptor`

| Additional field | Type | Notes |
|---|---|---|
| `testClass` | `Class<?>` | The `@Paramixel.TestClass`-annotated class |
| `argumentParallelism` | `int` | Set during discovery from collector; controls concurrent argument execution |

#### `ParamixelTestArgumentDescriptor` — extends `AbstractParamixelDescriptor`

| Additional field | Type | Notes |
|---|---|---|
| `argumentIndex` | `int` | Zero-based |
| `argument` | `Object` | The actual argument value (may be null if no supplier) |
| `displayName` | `String` | `Named.getName()` if argument implements `Named`, else `"argument:<idx>"` |

#### `ParamixelTestMethodDescriptor` — extends `AbstractParamixelDescriptor`

| Additional field | Type | Notes |
|---|---|---|
| `testMethod` | `java.lang.reflect.Method` | The `@Paramixel.Test`-annotated method |

---

## Lifecycle Execution Order (Sequence)

```
[Per class]
  @Paramixel.ArgumentsCollector (static method - no instance required)
  TestClass.newInstance()          ← no-arg constructor invocation (engine may make constructor accessible)
  @Paramixel.Initialize(ClassContext)
  [Per argument]
    @Paramixel.BeforeAll(ArgumentContext)
    [Per test method]
      @Paramixel.BeforeEach(ArgumentContext)
      @Paramixel.Test(ArgumentContext)
      @Paramixel.AfterEach(ArgumentContext)   ← only if @Paramixel.BeforeEach executed
    [End per test method]
    @Paramixel.AfterAll(ArgumentContext)      ← only if @Paramixel.BeforeAll executed
    argument.close()                ← if argument implements AutoCloseable
  [End per argument]
  @Paramixel.Finalize(ClassContext)           ← only if @Paramixel.Initialize executed
  testInstance.close()              ← if test instance implements AutoCloseable
[End per class]
```

**Important:** The `@Paramixel.ArgumentsCollector` method is static and executes before test class instantiation. If the test class cannot be instantiated (e.g., no-arg constructor fails), all test methods are marked FAILED and lifecycle hooks (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, etc.) are not executed.

**Lifecycle Pairing:** "After" hooks are paired with their corresponding "before" hooks:
- `@Paramixel.AfterEach` only executes if `@Paramixel.BeforeEach` was executed for that test method
- `@Paramixel.AfterAll` only executes if `@Paramixel.BeforeAll` was executed for that argument
- `@Paramixel.Finalize` only executes if `@Paramixel.Initialize` was executed for the class

This ensures cleanup only runs when setup has occurred.

Lifecycle hooks and `@Paramixel.Test` methods are discovered across the full class hierarchy and treated as a single flattened set per `@Paramixel.TestClass` (base/subclass location is irrelevant).

If the same method signature (name + parameter types) is annotated multiple times in the hierarchy for the same Paramixel annotation, only the most specific (subclass) declaration is used (the superclass declaration is ignored for that annotation).

For each annotation type (each lifecycle hook type and `@Paramixel.Test`), the resulting flattened method list is ordered deterministically:
- Primary: `@Paramixel.Order` value ascending
- Secondary: method name ascending
- Methods without `@Paramixel.Order` execute last (effective value = `Integer.MAX_VALUE`)

**Important:** The `@Paramixel.Order` annotation ordering is applied **per annotation type**. This means:
- All `@Paramixel.BeforeEach` methods are ordered among themselves
- All `@Paramixel.Test` methods are ordered among themselves
- All `@Paramixel.AfterEach` methods are ordered among themselves
- etc.

The ordering of one annotation type does not affect the ordering of another annotation type. For example, a `@Paramixel.BeforeEach` method with `@Paramixel.Order(1)` will still execute before all `@Paramixel.Test` methods, regardless of their `@Paramixel.Order` values.

---

## Configuration Properties

The engine reads `paramixel.properties` from the current working directory at runtime. Properties are also set programmatically at startup:

| Property key | Source | Description |
|---|---|---|
| `version` | Filtered at build time in engine's `paramixel.properties` | Engine version |
| `invokedBy` | Runtime, set by engine | `"maven"` or `"junit"` |
| `paramixel.parallelism` | Properties file or configuration parameters | Global maximum parallelism - the upper bound for concurrent test class execution (default: number of available processors) |

### Configuration Precedence

When a property is defined in multiple places, the following precedence applies (highest to lowest):

1. **JUnit Platform Configuration Parameters** (e.g., `-Dparamixel.parallelism=4`)
2. **Properties File** (`paramixel.properties`)
3. **Default Value**

This means configuration parameters override properties file settings.

### Parallelism Hierarchy

The engine supports two levels of parallelism configuration:

1. **Global Parallelism (`paramixel.parallelism`)**: Establishes the overall maximum number of concurrent test classes that may execute simultaneously across the entire test suite. This serves as the upper bound for all parallelism within the engine.

2. **Per-Class Parallelism (`ArgumentsCollector.setParallelism()`)**: Individual test classes may specify their own parallelism limit via `setParallelism()`. This value represents the maximum concurrency permitted for that specific test class. However, the effective parallelism for any class is constrained by the global `paramixel.parallelism` setting—the per-class value cannot exceed the global limit.

For example, if `paramixel.parallelism=4` (global) and a test class calls `setParallelism(8)` (per-class), the effective parallelism for that class will be 4.

The Maven plugin exposes additional parameters:

| Maven property | Default | Description |
|---|---|---|
| `paramixel.skipTests` | `false` | Skip all test execution |
| `paramixel.failIfNoTests` | `true` | Fail build if no `@Paramixel.TestClass` found |
| `paramixel.parallelism` | (engine default) | Global maximum parallelism (max concurrent test classes) |
| `paramixel.verbose` | `false` | Verbose logging (currently partially implemented) |
