# Paramixel -- Engine Internals

This spec documents the internal implementation types of `paramixel-engine`. These types
are not part of the public API and MUST NOT be imported by test authors.

## JUnit Platform Engine SPI

`ParamixelTestEngine` is registered via
`META-INF/services/org.junit.platform.engine.TestEngine` and implements
`org.junit.platform.engine.TestEngine`.

| Method | Contract |
|---|---|
| `getId()` | MUST return `"paramixel"` -- never null, never changes. |
| `discover(EngineDiscoveryRequest, UniqueId)` | MUST return a `ParamixelEngineDescriptor` populated with class/argument/method descriptors. MUST throw `IllegalStateException` if a `@Paramixel.TestClass` fails method validation. MUST NOT return null. |
| `execute(ExecutionRequest)` | MUST run all discovered tests. MUST report start/finish on the engine descriptor. MUST NOT throw. If any test fails, the engine descriptor MUST be finished with `TestExecutionResult.failed(...)`. |

---

## Concrete Context Implementations

### `ConcreteEngineContext` (`org.paramixel.engine.api`)

| Field | Type | Notes |
|---|---|---|
| `engineId` | `String` | Always `"paramixel"` |
| `configuration` | `Properties` | Defensive copy; loaded from `paramixel.properties` + runtime overrides |
| `classParallelism` | `int` | Max concurrent test classes; defaults to `availableProcessors()` |
| `store` | `Store` | `ConcreteStore` instance |

### `ConcreteClassContext` (`org.paramixel.engine.api`)

| Field | Type | Notes |
|---|---|---|
| `testClass` | `Class<?>` | Not null |
| `engineContext` | `EngineContext` | Not null |
| `testInstance` | `Object` | Nullable (null before instantiation) |
| `invocationCount` | `AtomicInteger` | Total invocations executed |
| `successCount` | `AtomicInteger` | Successful invocations |
| `failureCount` | `AtomicInteger` | Failed invocations |
| `firstFailure` | `AtomicReference<Throwable>` | Records first failure only; subsequent failures do not replace it |
| `store` | `Store` | Class-scoped `ConcreteStore` |
| `argumentContexts` | `ConcurrentHashMap<Integer, ConcreteArgumentContext>` | Cached per-argument contexts; removed after `@Paramixel.AfterAll` |

### `ConcreteArgumentContext` (`org.paramixel.engine.api`)

| Field | Type | Notes |
|---|---|---|
| `classContext` | `ConcreteClassContext` | Not null |
| `argument` | `Object` | Nullable |
| `argumentIndex` | `int` | Zero-based |
| `store` | `Store` | Argument-scoped `ConcreteStore` |

### `ConcreteArgumentsCollector` (`org.paramixel.engine.api`)

| Field | Type | Notes |
|---|---|---|
| `engineContext` | `EngineContext` | Not null |
| `arguments` | `List<Object>` | Ordered insertion list |
| `parallelism` | `int` | Defaults to `max(1, availableProcessors())`; constrained by global `paramixel.parallelism` |

### `ConcreteStore` (`org.paramixel.engine.api`)

Backed by `ConcurrentHashMap<String, Object>`. Null values cause removal (not storage).
All methods are null-safe on keys via `Objects.requireNonNull`.

---

## Descriptor Types

The JUnit Platform descriptor hierarchy:

```
ParamixelEngineDescriptor          (Type.CONTAINER)
  +-- ParamixelTestClassDescriptor   (Type.CONTAINER)
        +-- ParamixelTestArgumentDescriptor  (Type.CONTAINER)
              +-- ParamixelTestMethodDescriptor  (Type.TEST)
```

### `AbstractParamixelDescriptor` (`org.paramixel.engine.descriptor`)

Base class implementing `org.junit.platform.engine.TestDescriptor`.

| Field | Type | Notes |
|---|---|---|
| `uniqueId` | `UniqueId` | Format: `[engine:paramixel]/[class:<fqcn>]/[argument:<idx>]/[method:<name>]` |
| `displayName` | `String` | From `@Paramixel.DisplayName` or class/method name |
| `type` | `Type` | `CONTAINER` or `TEST` |
| `parent` | `TestDescriptor` | Nullable |
| `children` | `List<TestDescriptor>` | Ordered |

### `ParamixelTestClassDescriptor`

| Additional Field | Type | Notes |
|---|---|---|
| `testClass` | `Class<?>` | The `@Paramixel.TestClass`-annotated class |
| `argumentParallelism` | `int` | Set during discovery from collector |

### `ParamixelTestArgumentDescriptor`

| Additional Field | Type | Notes |
|---|---|---|
| `argumentIndex` | `int` | Zero-based |
| `argument` | `Object` | The actual argument value (may be null) |
| `displayName` | `String` | `Named.getName()` if argument implements `Named`, else `"argument:<idx>"` |

### `ParamixelTestMethodDescriptor`

| Additional Field | Type | Notes |
|---|---|---|
| `testMethod` | `java.lang.reflect.Method` | The `@Paramixel.Test`-annotated method |

---

## Key Engine Classes

| Class | Package | Role |
|---|---|---|
| `ParamixelTestEngine` | `engine` | JUnit Platform SPI entry; owns `discover()` + `execute()` |
| `ParamixelDiscovery` | `engine.discovery` | Stateless scanner; handles selector types; invokes `@Paramixel.ArgumentsCollector` at discovery time |
| `MethodValidator` | `engine.validation` | Static; validates all lifecycle method signatures; returns `ValidationFailure` list |
| `ParamixelExecutionRuntime` | `engine.execution` | Owns virtual-thread executor; `AutoCloseable` with 30s shutdown timeout |
| `ParamixelConcurrencyLimiter` | `engine.execution` | Fair semaphores: total=`cores*2`, class=`cores`, argument=`cores` |
| `ParamixelClassRunner` | `engine.execution` | Manages full class lifecycle; holds lifecycle method cache |
| `ParamixelInvocationRunner` | `engine.execution` | Per-argument method execution; supports ordered sequential or parallel modes |
| `ParamixelReflectionInvoker` | `engine.invoker` | Static utility; caches `setAccessible(true)` calls; unwraps `InvocationTargetException` |
| `ParamixelEngineExecutionListener` | `engine.listener` | Maven-mode listener; dispatches to type-specific sub-listeners |
| `AbstractEngineExecutionListener` | `engine.listener` | Base listener with `ExecutionSummary` counters |
| `FastIdUtil` | `engine.util` | Generates 6-char alphanumeric thread-name suffixes; avoids forbidden words |

---

## Configuration Properties

| Source | Key | Consumed By |
|---|---|---|
| JUnit Platform config | `invokedBy` | `ParamixelTestEngine.execute()` -- selects listener mode (`"maven"` or `"junit"`) |
| JUnit Platform config | `paramixel.parallelism` | `ParamixelTestEngine.execute()` -- sets class parallelism |
| `paramixel.properties` (file) | Any key | Available via `EngineContext.getConfigurationValue()` |
| `paramixel.properties` (built-in resource) | `version` | Engine version string |

### Configuration Precedence

When a property is defined in multiple places:

1. **JUnit Platform Configuration Parameters** (highest priority)
2. **Properties File** (`paramixel.properties` in project root)
3. **Default Value** (lowest priority)
