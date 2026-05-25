---
title: Migration 1.x to 5.x
description: Breaking API changes from 1.x to 5.x.
---

# Migration 1.x to 5.x

This guide covers all breaking changes when migrating from Paramixel 1.x directly to 5.x. It combines the 1.x→4.x changes with the 4.x→5.x changes.

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

## 1.x → 4.x Changes

### Action types (4.x)

The old specialized action classes have been replaced:

- `Direct.of("step", ctx -> {})` becomes `Direct.builder("step").runnable(ctx -> {}).build()`.
- `StrictSequential.of("suite", ...)` becomes `Container` with its default dependent policy.
- `Sequential` becomes `Container` with `ChildMode.INDEPENDENT` and `OrderMode.DECLARED`.
- `DependentSequential` becomes the default `Container` policy.
- `RandomSequential` becomes `Container.Policy` with `OrderMode.SHUFFLED` and `ChildMode.INDEPENDENT`.
- `DependentRandomSequential` becomes `Container` with `ChildMode.DEPENDENT` and `OrderMode.SHUFFLED`.
- `Lifecycle` becomes `Container.before(...).child(...).after(...)`.
- `Parallel.of(...)` becomes `Parallel.builder(...).child(...).build()`.

Use `Container.Policy` for behavior that used to be encoded by specific class names:

```java
Container.Policy.builder()
        .childMode(Container.ChildMode.INDEPENDENT)
        .orderMode(Container.OrderMode.SHUFFLED)
        .seed(42L)
        .build();
```

Context sharing is now action-owned. `Container` shares its context with `before` and `after` actions and creates child contexts for body children. Use `context.getAncestor("../")` to access parent state from body children.

:::note
In 5.x, `Container` is replaced by `Lifecycle` for before/after patterns. See [Action API changes (5.x)](#action-api-changes-5x) below.
:::

### Builder API changes (4.x)

The `children(List<Action>)` method has been removed from both `Container.Builder` and `Parallel.Builder`. Use chained `.child()` calls instead:

**Before (1.x):**

```java
Container.builder("suite")
        .children(List.of(action1, action2, action3))
        .build();
```

**After (4.x):**

```java
Container.builder("suite")
        .child(action1)
        .child(action2)
        .child(action3)
        .build();
```

For dynamic child lists, use a builder with a loop:

```java
var builder = Container.builder("suite");
for (Action action : actions) {
    builder.child(action);
}
return builder.build();
```

### Attachment → Store (4.x)

The single-attachment model has been replaced with a `Store` (key-value map).

**Before (1.x):**

```java
context.setAttachment(new MyData("value"));
MyData data = context.getAttachment()
        .flatMap(a -> a.to(MyData.class))
        .orElseThrow();
context.findAttachment(1)
        .flatMap(a -> a.to(MyData.class))
        .orElseThrow();
context.removeAttachment();
```

**After (4.x):**

```java
context.getStore().put("data", new MyData("value"));
MyData data = context.getStore()
        .get("data", MyData.class)
        .orElseThrow();
context.getAncestor("../").getStore()
        .get("data", MyData.class)
        .orElseThrow();
context.getStore().remove("data");
```

:::note
In 5.x, `Store` is removed entirely. Use `Instance` for per-execution state. See [Removed classes (5.x)](#removed-classes-5x) below.
:::

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

Null values are not supported by `Store`.

### Listener API (4.x)

Listener callbacks now receive `Result` instead of `Context` + `Action`. Use `result.getAction()` to access the action.

**Before (1.x):**

```java
void beforeAction(Context context, Action action)
void actionThrowable(Context context, Action action, Throwable throwable)
void afterAction(Context context, Action action, Result result)
```

**After (4.x):**

```java
void beforeAction(Result result)
void actionThrowable(Result result, Throwable throwable)
void afterAction(Result result)
void skipAction(Result result)
```

The `skipAction(Result)` callback fires for every skipped action.

:::note
In 5.x, listener method names and signatures change again. See [Listener API changes (5.x)](#listener-api-changes-5x) below.
:::

### ConsoleRunner → Runner (4.x)

`ConsoleRunner` no longer exists. Its methods are now on `Runner`.

**Before (1.x):**

```java
ConsoleRunner.runAndExit(action);
int exitCode = ConsoleRunner.runAndReturnExitCode(action);
```

**After (4.x):**

```java
try (Runner runner = Runner.builder().build()) {
    runner.runAndExit(action);
    int exitCode = runner.runAndReturnExitCode(action);
}
```

`Runner` extends `AutoCloseable`. Prefer try-with-resources so listeners that hold resources are closed reliably.

:::note
In 5.x, `Runner` no longer implements `AutoCloseable`. See [Runner API changes (5.x)](#runner-api-changes-5x) below.
:::

### Result and Status (4.x)

`Result` and `Status` are now interfaces.

#### Result

Before (1.x): `Result.of(status, elapsedTime)`, `Result.pass(...)`, `Result.fail(...)`, `Result.skip(...)`, `Result.staged()`.
After (4.x): Access `Result` through the `Runner.run(Action)` return value. The `Result` interface provides:

```java
Optional<Result> getParent()
List<Result> getChildren()
Action getAction()
Status getStatus()
Duration getRunDuration()
```

`getElapsedTime()` and `getCumulativeElapsedTime()` have been replaced by `getRunDuration()`.

| 1.x / 2.x | 4.x |
|---|---|
| `Result.getElapsedTime()` | `Result.getRunDuration()` |
| `Result.getCumulativeElapsedTime()` | `Result.getRunDuration()` |
| `ConcreteResult.setElapsedTime(Duration)` | `ConcreteResult.setRunDuration(Duration)` |
| `ConcreteResult(Action, Status, Duration elapsedTime)` | `ConcreteResult(Action, Status, Duration runDuration)` |

`getRunDuration()` returns the wall-clock time of the full run or skip. For leaf results this is the same as the old `getElapsedTime()`. For composed results, this returns the parent's own wall-clock duration, which inherently includes all child run time.

#### Status

Before (1.x): `Status.pass()`, `Status.failure(...)`, `Status.skip(...)`, `Status.staged()`, `isFail()`.
After (4.x): `Status` is an interface. Check status with `isPass()`, `isFailure()`, `isSkip()`. The `isFail()` method is now `isFailure()`.

:::note
In 5.x, status methods are renamed: `isPass()` → `isPassed()`, `isFailure()` → `isFailed()`. See [Result API changes (5.x)](#result-api-changes-5x) below.
:::

#### JSON and XML report format

The JSON report field `"elapsedTime"` has been renamed to `"runDuration"` and the `"cumulativeTime"` field has been removed. The XML report attribute `elapsedTime` has been renamed to `runDuration` and the `cumulativeTime` attribute has been removed.

### Selector builder (4.x)

`Selector.byPackageName()` and `Selector.byClassName()` are removed. `Selector` is now an interface with static factory methods and composition:

**Before (1.x):**

```java
Selector.byPackageName("com.example.tests");
Selector.byClassName("com.example.tests.MyTest");
```

**After (4.x):**

```java
Selector.packageRegex("com.example.tests");
Selector.packageOf(MyTest.class);
Selector.classRegex("com\\.example\\..*Test");
Selector.classOf(MyTest.class);
```

Combine criteria with `Selector.and()`, `Selector.or()`, and `Selector.not()`:

```java
Selector.and(Selector.packageRegex("com\\.example"), Selector.tagRegex("smoke"));
Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));
Selector.not(Selector.classRegex(".*Slow"));
```

Query methods on selectors are renamed: `.matchPackage(` → `.matchesPackage(`, `.matchClass(` → `.matchesClass(`, `.matchTag(` → `.matchesTag(`.

### findContext → findAncestor (4.x)

**Before (1.x):**

```java
context.findContext(1);
```

**After (4.x):**

```java
context.getAncestor("../");
```

Path-based API replaces integer levels. Use `get` for expected ancestors (throws `AncestorNotFoundException`), `find` for safe navigation (returns `Optional.empty()`).

### Ancestor navigation (4.x)

`findAncestor(int levelUp)` is replaced by `findAncestor(String path)`. The new path-based API uses path-like semantics for navigating the context hierarchy.

**Before (3.x):**

```java
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

`context.getParent()` now returns `Context` directly instead of `Optional<Context>`. Use `findParent()` for safe navigation when the parent might not exist.

:::note
In 5.x, `Context` is replaced by `ExecutionContext` and ancestor navigation is removed. See [Context to ExecutionContext (5.x)](#context-to-executioncontext-5x) below.
:::

### Resolver simplification (4.x)

The 14+ `Resolver` overloads have been reduced to 4:

```java
Optional<Action> resolveActions();
Optional<Action> resolveActions(Selector selector);
Optional<Action> resolveActions(Map<String, String> configuration);
Optional<Action> resolveActions(Map<String, String> configuration, Selector selector);
```

`Resolver.Composition` is removed. Discovered actions are always combined as `Parallel`.

:::note
In 5.x, `Resolver` is removed entirely. Discovery is handled internally by `Runner`. See [Resolver removal (5.x)](#resolver-removal-5x) below.
:::

### Exception packages (4.x)

Fail and skip exceptions moved from `org.paramixel.core` to `org.paramixel.core.exception`:

**Before (1.x):**

```java
import org.paramixel.core.FailException;
import org.paramixel.core.SkipException;
```

**After (4.x):**

```java
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;
```

All exception classes in `org.paramixel.core.exception`:

| Class | Description |
|---|---|
| `FailException` | Marks an action as failed |
| `SkipException` | Marks an action as skipped |
| `CycleDetectedException` | Thrown by cycle detection for parent-child cycles |
| `AncestorNotFoundException` | Ancestor context does not exist |

:::note
In 5.x, exception packages move from `org.paramixel.core.exception` to `org.paramixel.api.exception`. See [Package rename (5.x)](#package-rename-5x) below.
:::

### Runner lifecycle (4.x)

`Runner` extends `AutoCloseable`. Prefer try-with-resources so listeners that hold resources are closed reliably.

Use one runner for one execution boundary. Concurrent execution of the same or overlapping action trees is not supported.

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

#### Deadlock detection removed

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

### Report format configuration (4.x)

`paramixel.report.format` has been removed in 4.x. Select the report format with the `paramixel.report.file` extension:

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

Supported inferred formats are text, JSON, XML, and HTML.

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

### Listener API changes (5.x) {#listener-api-changes-5x}

| 4.x | 5.x |
| --- | --- |
| `Listener.runStarted(Runner)` | `Listener.onRunStarted()` |
| `Listener.beforeAction(Result)` | `Listener.onBeforeExecution(Descriptor)` |
| `Listener.actionThrowable(Result, Throwable)` | Removed — check `descriptor.metadata().throwable()` in `onAfterExecution` |
| `Listener.afterAction(Result)` | `Listener.onAfterExecution(Descriptor)` |
| `Listener.runCompleted(Runner, Result)` | `Listener.onRunCompleted(Result)` |
| `Listener.close()` | Removed — implement `AutoCloseable` yourself if needed |

### Result API changes (5.x) {#result-api-changes-5x}

| 4.x | 5.x |
| --- | --- |
| `Result.getStatus().isPass()` | `result.status().isPassed()` |
| `Result.getStatus().isFailure()` | `result.status().isFailed()` |
| `Result.getRunDuration()` | `Result.runDuration()` |
| `Result.getParent()` | Removed — results support downward-only navigation |
| `Result.getStore()` | Removed |
| `Result.getAction()` | Removed — access action identity through `descriptor.metadata()` |

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
- Replace `Sequential`, `DependentSequential`, `RandomSequential`, `DependentRandomSequential`, `Lifecycle`, `StrictSequential` with `Lifecycle` (5.x) for before/after patterns, or `Sequential` (5.x) for ordered children without before/after.
- Replace `Direct.of(...)` with `Step.of(name, ctx -> {})` (5.x).
- Replace `Parallel.builder(...).child(...).build()` (4.x) with `Parallel.of(name).child(...).resolve()` (5.x).
- Replace `children(List<Action>)` calls with chained `.child()` calls.
- Replace `setAttachment`/`getAttachment`/`findAttachment`/`removeAttachment` with `Instance` (5.x) for state propagation.
- Replace `ConsoleRunner` with `Runner`.
- Replace `isFail()` with `isFailed()` (5.x).
- Replace `isPass()` with `isPassed()`, `isFailure()` with `isFailed()`.
- Replace `getElapsedTime()`/`getCumulativeElapsedTime()` with `runDuration()`.
- Replace `Selector.byPackageName()`/`Selector.byClassName()` with `Selector.packageRegex()`/`Selector.classRegex()` and other static factory methods.
- Replace `findContext(N)` with `Instance` (5.x) for state propagation.
- Move exception imports from `org.paramixel.core.exception` to `org.paramixel.api.exception`.
- Update package imports from `org.paramixel.core` to `org.paramixel.api`.
- Replace `Factory` with `Runner`.
- Replace `Flow` with `Lifecycle`.
- Replace `Noop` with `Step.of(name, ctx -> {})`.
- Replace `Step.builder(name).runnable(...).build()` with `Step.of(name, ...)`.
- Replace `Context` with `ExecutionContext` in custom actions and context-mode steps.
- Remove `Store` usage — use `Instance` for state propagation.
- Replace `Flow.Policy` with `.independent()` / `.dependent()`.
- Update `Listener` method names and signatures.
- Remove `Runner.Builder.executorService(...)` calls.
- Replace `Context.getExecutorService()` usage with `Parallel` for concurrent execution.
- Remove `Parallel.Builder.executorService(...)` and `Parallel.Builder.scheduler(...)` calls — 5.x `Parallel.Spec` uses `parallelism(int)` instead.
- Remove nested `Parallel` deadlock workaround code that only existed to provide separate executors.
- Review custom actions for explicit listener callbacks, result construction, and context scoping.
- Implement `kind()` on all custom `Action` implementations.
- Replace `getRunDuration()` with `runDuration()`.
- Replace `Action.getName()` with `Action.name()`.
- Remove all `paramixel.report.format` usage — report format is now inferred from `paramixel.report.file` extension.
- Add `paramixel.properties` for classpath configuration if needed.
