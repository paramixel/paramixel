---
title: Migration 3.x to 5.x
description: Breaking API and behavior changes from 3.x to 5.x.
---

# Migration 3.x to 5.x

This guide covers all breaking changes when migrating from Paramixel 3.x directly to 5.x. It combines the 3.x→4.x changes with the 4.x→5.x changes.

## Dependency update

### Maven

```xml
<properties>
    <paramixel.version>5.0.0</paramixel.version>
</properties>
```

### Gradle

5.x does not provide a Gradle plugin. Use a `JavaExec` task with a custom source set:

```groovy
sourceSets {
    paramixel {
        java {
            srcDir 'src/paramixel/java'
        }
    }
}

dependencies {
    paramixelImplementation 'org.paramixel:core:5.0.0'
}

tasks.register('paramixelTest', JavaExec) {
    dependsOn 'paramixelClasses'
    group = 'verification'
    mainClass = 'org.paramixel.api.Runner'
    classpath = sourceSets.paramixel.runtimeClasspath
}
```

See [Gradle Integration](../integrations/gradle) for the full setup.

## 3.x → 4.x Changes

### Runner lifecycle (4.x)

`Runner` now extends `AutoCloseable` (4.x). Prefer try-with-resources so listeners that hold resources are closed reliably.

**Before (3.x):**

```java
Runner runner = Runner.builder().build();
Result result = runner.run(action);
```

**After (4.x):**

```java
try (Runner runner = Runner.builder().build()) {
    Result result = runner.run(action);
}
```

:::note
In 5.x, `Runner` no longer implements `AutoCloseable`. See [5.x Runner changes](#runner-api-changes-5x) below.
:::

### Executor APIs replaced by scheduler APIs (4.x)

4.x no longer exposes raw `ExecutorService` customization from the runner, context, or parallel action APIs.

Removed APIs:

- `Runner.Builder.executorService(ExecutorService)`
- `Context.getExecutorService()`
- `Parallel.Builder.executorService(ExecutorService)`

Replacement APIs:

- `Context.runAsync(Action)` for scheduling additional action work from an executing action
- `Parallel.Builder.scheduler(AsyncScheduler)` for advanced scheduler replacement inside a `Parallel` subtree
- `AsyncScheduler` for custom scheduling implementations

#### Context async execution

If custom action code used `context.getExecutorService()` to submit actions or action-like work, use `context.runAsync(action)` for Paramixel actions.

**Before (3.x):**

```java
ExecutorService executorService = context.getExecutorService();
Future<?> future = executorService.submit(() -> child.run(context));
future.get();
```

**After (4.x):**

```java
CompletableFuture<Result> future = context.runAsync(child);
Result result = future.join();
```

`Context.runAsync(action)` uses the effective scheduler for the current context. Inside a subtree with a custom `Parallel` scheduler, descendant `Context.runAsync(...)` calls use that custom scheduler.

:::note
In 5.x, `Context` is replaced by `ExecutionContext`. See [Context to ExecutionContext](#context-to-executioncontext-5x) below.
:::

#### Parallel scheduling

`Parallel.Builder.executorService(...)` is replaced by `Parallel.Builder.scheduler(...)`.

**Before (3.x):**

```java
private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

Action action = Parallel.builder("checks")
        .executorService(executorService)
        .parallelism(2)
        .child(checkA())
        .child(checkB())
        .build();
```

**After (4.x):**

```java
private static final AsyncScheduler scheduler = new MyScheduler();

Action action = Parallel.builder("checks")
        .scheduler(scheduler)
        .parallelism(2)
        .child(checkA())
        .child(checkB())
        .build();
```

A custom scheduler must complete the returned future with the action result or complete it exceptionally:

```java
public final class MyScheduler implements AsyncScheduler {

    @Override
    public CompletableFuture<Result> runAsync(Action action, Context context) {
        try {
            return CompletableFuture.completedFuture(action.run(context));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }
}
```

### Deadlock detection removed (4.x)

3.x guidance about nested `Parallel` deadlock detection and custom executor workarounds no longer applies.

The default scheduler owns worker execution in 4.x, so nested `Parallel` trees do not require separate executors to avoid thread-starvation deadlocks. Cycle detection still exists and still throws `CycleDetectedException` for parent-child cycles in the action graph.

### Custom actions (4.x)

Built-in actions are final framework primitives. Implement `Action` directly for custom behavior.

`Action` requires custom implementations to provide `getName()` and `execute(...)`.

Custom action implementations own:

- `run(Context)` and `skip(Context)` behavior
- null checks
- listener callbacks
- result construction and completion
- context scoping behavior (creating child contexts with `context.createChild()` when needed)

Custom actions must decide when to create child contexts. For composite actions, follow the built-in pattern: share the parent context with lifecycle actions (like `before`/`after`) and create child contexts for body children.

### Discovery priority (4.x)

4.x adds `@Paramixel.Priority` for ordering discovered factory classes before the framework builds the discovery root `Parallel`.

```java
@Paramixel.Priority(10)
public class SlowDatabaseTests {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action factory() {
        return Container.builder("SlowDatabaseTests")
                .child(testCreateDatabase())
                .child(testMigrateDatabase())
                .build();
    }
}
```

Priority defaults to `0`. Higher values are ordered earlier, and negative values are allowed.

### Report format configuration (4.x)

`paramixel.report.format` has been removed in 4.x. Select the report format with the `paramixel.report.file` extension:

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

Supported inferred formats are text, JSON, XML, and HTML.

### Store Value removal (4.x)

4.x removes the `Value` wrapper class from the Store API. Store methods now accept and return plain objects directly, and new typed convenience methods provide safe type casting.

**Before (3.x):**

```java
context.getStore().put("key", Value.of(myObject));
MyType data = context.getStore().get("key").orElseThrow().cast(MyType.class);
boolean matches = context.getStore().get("key").orElseThrow().isType(MyType.class);
context.getStore().compute("counter", (k, v) -> Value.of(v.cast(Integer.class) + 1));
context.getStore().putIfAbsent("key", Value.of("default"));
context.getStore().merge("key", Value.of(newValue), (oldVal, newVal) -> Value.of("merged"));
```

**After (4.x):**

```java
context.getStore().put("key", myObject);
MyType data = context.getStore().get("key", MyType.class).orElseThrow();
boolean matches = context.getStore().isType("key", MyType.class);
context.getStore().compute("counter", (k, v) -> (Integer) v + 1);
context.getStore().putIfAbsent("key", "default");
context.getStore().merge("key", newValue, (oldVal, newVal) -> "merged");
```

New typed convenience methods on `Store`:

| Method | Description |
|---|---|
| `get(String key, Class<T> type)` | Returns `Optional<T>` — combines get and cast |
| `remove(String key, Class<T> type)` | Returns `Optional<T>` — combines remove and cast |
| `getOrDefault(String key, Class<T> type, T defaultValue)` | Returns typed value or default |
| `isType(String key, Class<?> type)` | Returns `true` when key is present and value is compatible |

Null values are not supported by `Store` — the same contract as `Value.of()` which rejected null.

:::note
In 5.x, `Store` is removed entirely. See [Removed classes (5.x)](#removed-classes-5x) below.
:::

### Ancestor navigation path-based API (4.x)

`findAncestor(int levelUp)` is replaced by `findAncestor(String path)`. The new path-based API uses path-like semantics for navigating the context hierarchy.

**Before (3.x):**

```java
context.findAncestor(0)  // current context
context.findAncestor(1)  // parent
context.findAncestor(2)  // grandparent
```

**After (4.x):**

```java
context.getParent()            // parent (throws AncestorNotFoundException at root)
context.getAncestor("../")     // parent
context.getAncestor("../../")  // grandparent
context.getAncestor("/")       // root
```

Path segments use `../` for each parent hop. Use `get` methods when the ancestor is expected to exist (throws `AncestorNotFoundException`). Use `find` methods for safe navigation (returns `Optional.empty()`).

:::note
In 5.x, `Context` is replaced by `ExecutionContext` and ancestor navigation is removed. See [Context to ExecutionContext (5.x)](#context-to-executioncontext-5x) below.
:::

### New exception classes (4.x)

All in `org.paramixel.core.exception`:

| Class | Description |
|---|---|
| `AncestorNotFoundException` | Ancestor context does not exist |

## 4.x → 5.x Changes

### Package rename (5.x) {#package-rename-5x}

| 4.x | 5.x |
| --- | --- |
| `org.paramixel.core` | `org.paramixel.api` |
| `org.paramixel.core.action` | `org.paramixel.api.action` |
| `org.paramixel.core.exception` | `org.paramixel.api.exception` |
| `org.paramixel.core.support` | `org.paramixel.api.support` |

### Removed classes (5.x) {#removed-classes-5x}

| Removed | Replacement |
| --- | --- |
| `Factory` | `Runner` — use `Runner.defaultRunner()` instead of `Factory.defaultRunner()` |
| `Flow` | `Lifecycle` — same before/body/after pattern, new spec API |
| `Noop` | Removed — use `Step.of("name", ctx -> {})` for a no-op action |
| `Context` | `ExecutionContext` — provides configuration, listener, descriptor, instance access, status setters, and child scheduling |
| `Store` | Removed — use `Instance` for per-execution state, or `ExecutionContext#instance(Class)` |

### Action API changes (5.x) {#action-api-changes-5x}

| 4.x | 5.x |
| --- | --- |
| `Step.builder(name).runnable(ctx -> {}).build()` | `Step.of(name, ctx -> {})` |
| `Flow.builder(name).before(action).child(action).after(action).build()` | `Lifecycle.of(name).before(action).child(action).after(action).resolve()` |
| `Flow.Policy` | Removed — use `Lifecycle.of(name).independent()` or `.dependent()` |
| `Flow.ChildMode.INDEPENDENT` | `Lifecycle.of(name).independent()` |
| `Flow.ChildMode.DEPENDENT` | `Lifecycle.of(name).dependent()` (default) |
| `Noop.of(name)` | `Step.of(name, ctx -> {})` |
| `Action.getName()` | `Action.name()` |
| Custom actions implement only `getName()`, `execute()` | Custom actions must also implement `kind()` |

### New action types (5.x)

| Type | Description |
| --- | --- |
| `Sequential` | Ordered dependent or independent children without before/after |
| `Instance` | Factory-created instance with automatic lifecycle and `AutoCloseable` teardown |
| `Static` | Instance-free before/body/after lifecycle |

### Context to ExecutionContext (5.x) {#context-to-executioncontext-5x}

| 4.x `Context` | 5.x `ExecutionContext` |
| --- | --- |
| `context.getConfiguration()` | `context.configuration()` |
| `context.getStore()` | Removed — use `Instance` or `context.instance(Class)` |
| `context.getListener()` | `context.listener()` |
| `context.getAncestor(path)` | Removed — use `Instance` for state propagation |
| `context.runAsync(action)` | Removed — use `Parallel` for concurrent execution |
| `context.createChild()` | Removed — handled by framework composites |

### Runner API changes (5.x) {#runner-api-changes-5x}

| 4.x | 5.x |
| --- | --- |
| `Factory.defaultRunner()` | `Runner.defaultRunner()` |
| `Factory.defaultRunner().runAndExit(action)` | `Runner.defaultRunner().runAndExit(action)` |
| `Runner.run(Action)` | `Runner.run(Action)` (unchanged) |
| `Runner` implements `AutoCloseable` | `Runner` no longer implements `AutoCloseable` |

### Listener API changes (5.x)

| 4.x | 5.x |
| --- | --- |
| `Listener.runStarted(Runner)` | `Listener.onRunStarted()` |
| `Listener.beforeAction(Result)` | `Listener.onBeforeExecution(Descriptor)` |
| `Listener.actionThrowable(Result, Throwable)` | Removed — check `descriptor.metadata().throwable()` in `onAfterExecution` |
| `Listener.afterAction(Result)` | `Listener.onAfterExecution(Descriptor)` |
| `Listener.runCompleted(Runner, Result)` | `Listener.onRunCompleted(Result)` |
| `Listener.close()` | Removed — implement `AutoCloseable` yourself if needed |

### Result API changes (5.x)

| 4.x | 5.x |
| --- | --- |
| `Result.getStatus().isPass()` | `result.status().isPassed()` |
| `Result.getStatus().isFailure()` | `result.status().isFailed()` |
| `Result.getRunDuration()` | `Result.runDuration()` |
| `Result.getParent()` | Removed — results support downward-only navigation |
| `Result.getStore()` | Removed |

### Configuration changes (5.x)

| 4.x | 5.x |
| --- | --- |
| `Configuration.RUNNER_PARALLELISM` | `Configuration.RUNNER_PARALLELISM` (unchanged) |
| `Configuration.REPORT_FILE` | `Configuration.REPORT_FILE` (unchanged) |
| `Configuration.FAILURE_ON_SKIP` | `Configuration.FAILURE_ON_SKIP` (unchanged) |

New configuration keys in 5.x:

| Key | Description |
| --- | --- |
| `paramixel.scheduler.queue.capacity` | Max scheduler-ready tasks (default 1024) |
| `paramixel.failureOnAbort` | Whether ABORTED produces failing exit code (default true) |
| `paramixel.ansi` | ANSI output control (`true`, `false`, `auto`) |
| `paramixel.failIfNoTests` | Fail when no action factories are discovered |

### Resolver removal (5.x)

`Resolver` is removed. Discovery is now an internal concern of `Runner`. Use the `Runner` methods that accept a `Selector`:

```java
Runner runner = Runner.defaultRunner();
Optional<Result> result = runner.run(selector);   // resolve + execute
int exitCode = runner.run();                       // discover all + execute
int exitCode = runner.runAndReturnExitCode(selector);
runner.runAndExit(selector);
```

### New: AnnotationResolver (5.x)

5.x introduces `AnnotationResolver` for resolving `@Paramixel.Id` methods:

```java
AnnotationResolver<MyTest> resolver = AnnotationResolver.create(MyTest.class);
Action<MyTest> login = resolver.byId("login");
```

### New: Retry and CleanUp (5.x)

5.x moves `Retry` and `CleanUp` to `org.paramixel.api.support`:

```java
import org.paramixel.api.support.Retry;
import org.paramixel.api.support.CleanUp;
```

## Migration checklist

- Update dependencies and plugins to `5.0.0`.
- Update package imports from `org.paramixel.core` to `org.paramixel.api`.
- Replace `Factory` with `Runner`.
- Replace `Flow` with `Lifecycle`.
- Replace `Noop` with `Step.of(name, ctx -> {})`.
- Replace `Step.builder(name).runnable(...).build()` with `Step.of(name, ...)`.
- Replace `Context` with `ExecutionContext` in custom actions and context-mode steps.
- Remove `Store` usage — use `Instance` for state propagation.
- Replace `Flow.Policy` with `.independent()` / `.dependent()`.
- Replace `isPass()` with `isPassed()`, `isFailure()` with `isFailed()`.
- Update `Listener` method names and signatures.
- Remove `Runner.Builder.executorService(...)` calls.
- Replace `Context.getExecutorService()` usage with `Parallel` for concurrent execution.
- Remove `Parallel.Builder.executorService(...)` and `Parallel.Builder.scheduler(...)` calls — 5.x `Parallel.Spec` uses `parallelism(int)` instead.
- Remove nested `Parallel` deadlock workaround code that only existed to provide separate executors.
- Review custom actions for explicit listener callbacks, result construction, and context scoping.
- Implement `kind()` on all custom `Action` implementations.
- Replace `getRunDuration()` with `runDuration()`.
- Replace `Action.getName()` with `Action.name()`.
- Use `@Paramixel.Priority` if discovered root admission order matters.
- Remove all `paramixel.report.format` usage — report format is now inferred from `paramixel.report.file` extension.
- Add `paramixel.properties` for classpath configuration if needed.
