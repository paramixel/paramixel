---
title: Migration 1.x to 4.x
description: Breaking API changes from 1.x to 4.x.
---

# Migration 1.x to 4.x

4.x replaces the old specialized action classes with `Container`, `Parallel`, `Direct`, and `Noop`, replaces direct executor customization with scheduler-based async running, removes the `Store` `Value` wrapper, and tightens the action run contract for custom actions.

## Action types

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

## Builder API changes

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

## Attachment → Store

The single-attachment model has been replaced with a `Store` (key-value map).

### Before (1.x)

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

### After (4.x)

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

## Store Value removal

4.x removes the `Value` wrapper class from the Store API. Store methods now accept and return plain objects directly, and new typed convenience methods provide safe type casting.

### Before (3.x)

```java
context.getStore().put("key", Value.of(myObject));
MyType data = context.getStore().get("key").orElseThrow().cast(MyType.class);
boolean matches = context.getStore().get("key").orElseThrow().isType(MyType.class);
context.getStore().compute("counter", (k, v) -> Value.of(v.cast(Integer.class) + 1));
context.getStore().putIfAbsent("key", Value.of("default"));
context.getStore().merge("key", Value.of(newValue), (oldVal, newVal) -> Value.of("merged"));
```

### After (4.x)

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

## Listener API

Listener callbacks now receive `Result` instead of `Context` + `Action`. Use `result.getAction()` to access the action.

### Before (1.x)

```java
void beforeAction(Context context, Action action)
void actionThrowable(Context context, Action action, Throwable throwable)
void afterAction(Context context, Action action, Result result)
```

### After (4.x)

```java
void beforeAction(Result result)
void actionThrowable(Result result, Throwable throwable)
void afterAction(Result result)
void skipAction(Result result)
```

The `skipAction(Result)` callback fires for every skipped action.

## ConsoleRunner → Runner

`ConsoleRunner` no longer exists. Its methods are now on `Runner`.

### Before (1.x)

```java
ConsoleRunner.runAndExit(action);
int exitCode = ConsoleRunner.runAndReturnExitCode(action);
```

### After (4.x)

```java
try (Runner runner = Runner.builder().build()) {
    runner.runAndExit(action);
    int exitCode = runner.runAndReturnExitCode(action);
}
```

`Runner` extends `AutoCloseable`. Prefer try-with-resources so listeners that hold resources are closed reliably.

## Result and Status

`Result` and `Status` are now interfaces.

### Result

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
| `DefaultResult.setElapsedTime(Duration)` | `DefaultResult.setRunDuration(Duration)` |
| `DefaultResult(Action, Status, Duration elapsedTime)` | `DefaultResult(Action, Status, Duration runDuration)` |

`getRunDuration()` returns the wall-clock time of the full run or skip. For leaf results this is the same as the old `getElapsedTime()`. For composed results, this returns the parent's own wall-clock duration, which inherently includes all child run time. The old `getCumulativeElapsedTime()` summed children's times separately for composed results, which was misleading — consumers who need aggregated child timing should compute it from the result tree.

### Status

Before (1.x): `Status.pass()`, `Status.failure(...)`, `Status.skip(...)`, `Status.staged()`, `isFail()`.
After (4.x): `Status` is an interface. Check status with `isPass()`, `isFailure()`, `isSkip()`. The `isFail()` method is now `isFailure()`.

### JSON and XML report format

The JSON report field `"elapsedTime"` has been renamed to `"runDuration"` and the `"cumulativeTime"` field has been removed. The XML report attribute `elapsedTime` has been renamed to `runDuration` and the `cumulativeTime` attribute has been removed. The HTML report's embedded JSON uses `runDuration` consistently with the standalone JSON and XML reports.

## Selector builder

`Selector.byPackageName()` and `Selector.byClassName()` are removed. Use `Selector.builder()`:

### Before (1.x)

```java
Selector.byPackageName("com.example.tests");
Selector.byClassName("com.example.tests.MyTest");
```

### After (4.x)

```java
Selector.builder().packageMatch("com.example.tests").build();
Selector.builder().packageOf(MyTest.class).build();
Selector.builder().classMatch("com\\.example\\..*Test").build();
Selector.builder().classOf(MyTest.class).build();
```

## findContext → findAncestor

### Before (1.x)

```java
context.findContext(1);
```

### After (4.x)

```java
context.getAncestor("../");
```

Path-based API replaces integer levels. Use `get` for expected ancestors (throws `AncestorNotFoundException`), `find` for safe navigation (returns `Optional.empty()`).

## Ancestor navigation

`findAncestor(int levelUp)` is replaced by `findAncestor(String path)`. The new path-based API uses path-like semantics for navigating the context hierarchy.

### Before (3.x)

```java
context.findAncestor(1)  // parent
context.findAncestor(2)  // grandparent
```

### After (4.x)

```java
context.getParent()            // parent (throws AncestorNotFoundException at root)
context.getAncestor("../")     // parent
context.getAncestor("../../")  // grandparent
context.getAncestor("/")       // root
```

Path segments use `../` for each parent hop. Use `get` methods when the ancestor is expected to exist (throws `AncestorNotFoundException`). Use `find` methods for safe navigation (returns `Optional.empty()`).

`context.getParent()` now returns `Context` directly instead of `Optional<Context>`. Use `findParent()` for safe navigation when the parent might not exist.

## Resolver simplification

The 14+ `Resolver` overloads have been reduced to 4:

```java
Optional<Action> resolveActions();
Optional<Action> resolveActions(Selector selector);
Optional<Action> resolveActions(Map<String, String> configuration);
Optional<Action> resolveActions(Map<String, String> configuration, Selector selector);
```

`Resolver.Composition` is removed. Discovered actions are always combined as `Parallel`.

## Exception packages

Fail and skip exceptions moved from `org.paramixel.core` to `org.paramixel.core.exception`:

### Before (1.x)

```java
import org.paramixel.core.FailException;
import org.paramixel.core.SkipException;
```

### After (4.x)

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

## Runner lifecycle

`Runner` extends `AutoCloseable`. Prefer try-with-resources so listeners that hold resources are closed reliably.

Use one runner for one execution boundary. Concurrent execution of the same or overlapping action trees is not supported.

## Executor APIs replaced by scheduler APIs

4.x no longer exposes raw `ExecutorService` customization from the runner, context, or parallel action APIs.

Removed APIs:

- `Runner.Builder.executorService(ExecutorService)`
- `Context.getExecutorService()`
- `Parallel.Builder.executorService(ExecutorService)`

Replacement APIs:

- `Context.runAsync(Action)` for scheduling additional action work from an executing action
- `Parallel.Builder.scheduler(AsyncScheduler)` for advanced scheduler replacement inside a `Parallel` subtree
- `AsyncScheduler` for custom scheduling implementations

### Context async execution

If custom action code used `context.getExecutorService()` to submit actions or action-like work, use `context.runAsync(action)` for Paramixel actions.

### Before (3.x)

```java
ExecutorService executorService = context.getExecutorService();
Future<?> future = executorService.submit(() -> child.run(context));
future.get();
```

### After (4.x)

```java
CompletableFuture<Result> future = context.runAsync(child);
Result result = future.join();
```

`Context.runAsync(action)` uses the effective scheduler for the current context. Inside a subtree with a custom `Parallel` scheduler, descendant `Context.runAsync(...)` calls use that custom scheduler.

### Parallel scheduling

`Parallel.Builder.executorService(...)` is replaced by `Parallel.Builder.scheduler(...)`.

### Before (3.x)

```java
private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

Action action = Parallel.builder("checks")
        .executorService(executorService)
        .parallelism(2)
        .child(checkA())
        .child(checkB())
        .build();
```

### After (4.x)

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

`Parallel.parallelism(int)` limits direct-child admission for that `Parallel` node. `paramixel.parallelism` controls global worker concurrency for the default scheduler. When a custom scheduler is used, the scheduler owns any executor sizing, queuing, backpressure, and nested `Context.runAsync(...)` limits inside that subtree.

### Deadlock detection removed

The default scheduler owns worker execution in 4.x, so nested `Parallel` trees do not require separate executors to avoid thread-starvation deadlocks. Cycle detection still exists and still throws `CycleDetectedException` for parent-child cycles in the action graph.

## Custom actions

Built-in actions are final framework primitives. Implement `Action` directly or extend `AbstractAction` for custom behavior.

`AbstractAction` provides generated IDs and name validation helpers, with final accessors for `getId()` and `getName()`.

`AbstractAction` does not wrap execution. Custom action implementations own:

- `run(Context)` and `skip(Context)` behavior
- null checks
- listener callbacks
- result construction and completion
- context scoping behavior (creating child contexts with `context.createChild()` when needed)

Custom actions must decide when to create child contexts. For composite actions, follow the built-in pattern: share the parent context with lifecycle actions (like `before`/`after`) and create child contexts for body children.

## Report format configuration

`paramixel.report.format` has been removed in 4.x. Select the report format with the `paramixel.report.file` extension:

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

Supported inferred formats are text, JSON, XML, and HTML.

## Migration checklist

- Replace `Sequential`, `DependentSequential`, `RandomSequential`, `DependentRandomSequential`, `Lifecycle`, `StrictSequential` with `Container` and `Container.Policy`.
- Replace `Direct.of(...)` with `Direct.builder(...).runnable(...).build()`.
- Replace `Parallel.of(...)` with `Parallel.builder(...).child(...).build()`.
- Replace `children(List<Action>)` calls with chained `.child()` calls.
- Replace `setAttachment`/`getAttachment`/`findAttachment`/`removeAttachment` with `Store` methods.
- Remove all `Value.of()` wrapping from `Store.put()` and `Store.putIfAbsent()` calls.
- Replace `.get(key).orElseThrow().cast(T.class)` with `.get(key, T.class).orElseThrow()`.
- Replace `.remove(key).orElseThrow().cast(T.class)` with `.remove(key, T.class).orElseThrow()`.
- Replace `Value.isType()` checks with `Store.isType(key, type)`.
- Update `compute`/`merge`/`replaceAll` lambdas to use raw `Object` types instead of `Value`.
- Replace `findAncestor(N)` with `getAncestor(path)` using `../` per parent hop (e.g., `findAncestor(2)` → `getAncestor("../../")`).
- Replace `context.getParent().orElseThrow()` with `context.getParent()` (now returns Context directly, throws `AncestorNotFoundException` at root).
- Use `findParent()` and `findAncestor(path)` for safe navigation when the ancestor might not exist.
- Replace `ConsoleRunner` with `Runner` in try-with-resources.
- Replace `isFail()` with `isFailure()`.
- Replace `getElapsedTime()`/`getCumulativeElapsedTime()` with `getRunDuration()`.
- Replace `Selector.byPackageName()`/`Selector.byClassName()` with `Selector.builder()`.
- Replace `findContext(N)` with `getAncestor(path)`.
- Move `FailException` and `SkipException` imports to `org.paramixel.core.exception`.
- Remove `Runner.Builder.executorService(...)` calls.
- Replace `Context.getExecutorService()` usage with `Context.runAsync(action)` for Paramixel actions.
- Replace `Parallel.Builder.executorService(...)` with `Parallel.Builder.scheduler(...)` only when custom scheduling is still needed.
- Remove nested `Parallel` deadlock workaround code that only existed to provide separate executors.
- Review custom actions for explicit listener callbacks, result construction, and context scoping.
- Remove all `paramixel.report.format` usage — report format is now inferred from `paramixel.report.file` extension.
