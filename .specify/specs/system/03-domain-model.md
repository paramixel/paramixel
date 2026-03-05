# Paramixel -- Domain Model

Paramixel is a test framework, not a data-persistence application. There are no JPA entities,
no databases, and no ORM mappings. The "domain model" consists of public API interfaces,
annotations, and value objects that carry state through the test execution pipeline.

## Definitions

- **Test class**: A class annotated with `@Paramixel.TestClass`.
- **Argument**: A value supplied by `@Paramixel.ArgumentsCollector` and paired with test methods.
- **Argument bucket**: The set of all test method invocations for one argument value.
- **Lifecycle hook**: A method annotated with a lifecycle annotation (`@Paramixel.Initialize`, `@Paramixel.BeforeAll`, `@Paramixel.BeforeEach`, `@Paramixel.AfterEach`, `@Paramixel.AfterAll`, `@Paramixel.Finalize`).
- **Store**: A scoped key-value map for sharing state within a lifecycle scope.
- **Context**: An object providing access to the current execution scope's state, store, and parent scope.

---

## Public API Interfaces (`paramixel-api`)

### `ArgumentContext`

Package: `org.paramixel.api`

Represents context for a single argument invocation (one argument value x one test class
instance). Injected into `@Paramixel.BeforeAll`, `@Paramixel.BeforeEach`, `@Paramixel.Test`,
`@Paramixel.AfterEach`, and `@Paramixel.AfterAll` methods.

From an `ArgumentContext`, the parent `ClassContext` is accessible via `getClassContext()`,
and the root `EngineContext` via `getClassContext().getEngineContext()`.

| Method | Return Type | Nullable | Description |
|---|---|---|---|
| `getClassContext()` | `ClassContext` | no | Parent class-level context |
| `getStore()` | `Store` | no | Argument-scoped key-value store |
| `getArgument()` | `Object` | yes | The argument value; `null` if no arguments collector defined |
| `getArgument(Class<T>)` | `T` | yes | Type-safe cast of `getArgument()` |
| `getArgumentIndex()` | `int` | n/a | Zero-based position of this argument in collector output |

**Method Contract -- `getArgument(Class<T> type)`:**
- If `getArgument()` returns `null`: returns `null` (no cast attempted).
- If `getArgument()` is an instance of `type`: returns `type.cast(getArgument())`.
- Otherwise: throws `ClassCastException`.
- Throws `NullPointerException` if `type` is `null`.

---

### `ArgumentsCollector`

Package: `org.paramixel.api`

Passed to a `@Paramixel.ArgumentsCollector` method. Allows programmatic argument
registration and per-class parallelism override.

| Method | Return Type | Description |
|---|---|---|
| `getEngineContext()` | `EngineContext` | Parent engine context |
| `addArgument(Object)` | `ArgumentsCollector` | Registers one argument value; returns `this` |
| `addArguments(Object...)` | `ArgumentsCollector` | Registers multiple arguments; returns `this` |
| `addArguments(List<?>)` | `ArgumentsCollector` | Registers a list of arguments; returns `this` |
| `setParallelism(int)` | `ArgumentsCollector` | Sets per-class argument parallelism (>= 1); returns `this` |

---

### `ClassContext`

Package: `org.paramixel.api`

Represents context for one test class. Injected into `@Paramixel.Initialize` and
`@Paramixel.Finalize` methods. Accessible via `ArgumentContext.getClassContext()`.

From a `ClassContext`, the root `EngineContext` is accessible via `getEngineContext()`.

| Method | Return Type | Nullable | Description |
|---|---|---|---|
| `getEngineContext()` | `EngineContext` | no | Parent engine context |
| `getStore()` | `Store` | no | Class-scoped key-value store (shared across all arguments) |
| `getTestClass()` | `Class<?>` | no | The `Class` object for the test class |
| `getTestInstance()` | `Object` | yes | The instantiated test object; `null` before instantiation |

---

### `EngineContext`

Package: `org.paramixel.api`

Root context, shared across all test classes and arguments for one engine run.
Accessible via `ClassContext.getEngineContext()`.

| Method | Return Type | Nullable | Description |
|---|---|---|---|
| `getEngineId()` | `String` | no | Always `"paramixel"` |
| `getConfiguration()` | `Properties` | no | Defensive copy of engine configuration |
| `getConfigurationValue(String)` | `String` | yes | Value for key; `null` if absent |
| `getConfigurationValue(String, String)` | `String` | yes | Value for key; default if absent |
| `getStore()` | `Store` | no | Engine-scoped key-value store (shared across all classes) |

**Method Contract -- `getConfigurationValue(String key, String defaultValue)`:**
- Returns `configuration.getProperty(key, defaultValue)`.
- Throws `NullPointerException` if `key` is `null`.
- `defaultValue` MAY be `null`; if key is absent, `null` is returned.

---

### `Store`

Package: `org.paramixel.api`

Generic key-value store with typed access and `computeIfAbsent` semantics.
Implementations MUST be thread-safe.

| Method | Return Type | Description |
|---|---|---|
| `put(String, Object)` | `Object` | Stores value; returns previous. `null` value is equivalent to `remove(key)`. |
| `put(String, Class<T>, T)` | `T` | Typed put with previous-value type check |
| `get(String)` | `Object` | Raw get; `null` if absent |
| `get(String, Class<T>)` | `T` | Typed get; throws `ClassCastException` on type mismatch |
| `contains(String)` | `boolean` | Presence check |
| `remove(String)` | `Object` | Removes and returns previous |
| `remove(String, Class<T>)` | `T` | Typed remove |
| `clear()` | `void` | Removes all entries |
| `size()` | `int` | Entry count |
| `computeIfAbsent(String, Supplier<?>)` | `Object` | Compute-if-absent; supplier MAY be invoked more than once under contention |
| `computeIfAbsent(String, Class<T>, Supplier<T>)` | `T` | Typed compute-if-absent |
| `find(String, Class<T>)` | `Optional<T>` | Optional-returning typed get |
| `keyIterator()` | `Iterator<String>` | Iterates over keys (order unspecified) |

**Method Contract -- `put(String key, Object value)`:**
- Stores value under key. If `value` is `null`, equivalent to `remove(key)`.
- Throws `NullPointerException` if `key` is `null`.
- Returns the previous value, or `null` if none.

**Method Contract -- `computeIfAbsent(String key, Supplier<?> supplier)`:**
- If key is already mapped: returns existing value without invoking supplier.
- If key is absent: invokes supplier, stores result (unless supplier returns `null`).
- Supplier MAY be called more than once under contention (`ConcurrentHashMap` semantics).
- Throws `NullPointerException` if `key` or `supplier` is `null`.

---

### `Named`

Package: `org.paramixel.api`

Optional interface for argument objects that want a custom display name in test reports.
If an argument implements `Named`, its `getName()` result becomes the argument display name
in the descriptor tree.

| Method | Return Type | Description |
|---|---|---|
| `getName()` | `String` | Display name; MUST return non-null |

---

### `NamedValue<T>`

Package: `org.paramixel.api`

Concrete generic value object pairing a name with an arbitrary payload. Implements `Named`.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `name` | `String` | no | Display name (`@NonNull`) |
| `value` | `T` | yes | Wrapped payload |

- **Factory method:** `NamedValue.of(String name, T value)` -- static, returns new instance.
- **Accessors:** `getName()`, `getValue()`, `getValue(Class<V>)` (typed cast).

---

## Annotations

All annotations are nested inside `org.paramixel.api.Paramixel`. All carry
`@Retention(RetentionPolicy.RUNTIME)` and `@Documented`.

| Annotation | Target | Required Signature | Description |
|---|---|---|---|
| `@Paramixel.TestClass` | `TYPE` | N/A (class-level) | Marks a class for engine discovery |
| `@Paramixel.Test` | `METHOD` | `public void name(ArgumentContext)` -- instance, not static | Declares a test method |
| `@Paramixel.ArgumentsCollector` | `METHOD` | `public static void name(ArgumentsCollector)` | Supplies test arguments |
| `@Paramixel.Initialize` | `METHOD` | `public void name(ClassContext)` -- instance, not static | Once per class, before all lifecycle |
| `@Paramixel.BeforeAll` | `METHOD` | `public void name(ArgumentContext)` -- may be static | Once per argument, before all tests |
| `@Paramixel.BeforeEach` | `METHOD` | `public void name(ArgumentContext)` -- instance, not static | Before each test method invocation |
| `@Paramixel.AfterEach` | `METHOD` | `public void name(ArgumentContext)` -- instance, not static | After each test method invocation |
| `@Paramixel.AfterAll` | `METHOD` | `public void name(ArgumentContext)` -- may be static | Once per argument, after all tests |
| `@Paramixel.Finalize` | `METHOD` | `public void name(ClassContext)` -- instance, not static | Once per class, after all lifecycle (guaranteed if `@Paramixel.Initialize` ran) |
| `@Paramixel.Disabled` | `TYPE`, `METHOD` | N/A | Skips the annotated class or method; optional `value()` for reason |
| `@Paramixel.DisplayName` | `TYPE`, `METHOD` | N/A | Overrides display name; required `value()` |
| `@Paramixel.Order` | `METHOD` | N/A | Explicit ordering; `value()` MUST be > 0; lower = earlier |
| `@Paramixel.Tags` | `TYPE` | N/A | Categorizes test class with tags for filtering |

For detailed annotation contracts including validation errors, inheritance rules,
and failure behavior, see `04-lifecycle.md` and `08-error-handling.md`.

---

## Context Hierarchy

The context hierarchy mirrors the test execution hierarchy. Each level has its own `Store`.

```
EngineContext (engine-scoped Store)
  +-- ClassContext (class-scoped Store, shared across all arguments)
        +-- ArgumentContext (argument-scoped Store, one per argument)
```

- **Engine store**: Shared across all test classes in one engine run.
- **Class store**: Shared across all arguments for one test class.
- **Argument store**: Isolated to one argument invocation.

Navigation is top-down only: `ArgumentContext.getClassContext().getEngineContext()`.
There is no downward navigation from `EngineContext` to its children.
