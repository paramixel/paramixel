---
title: Migration 3.x to 4.x
description: Breaking API and behavior changes from 3.x to 4.x.
---

# Migration 3.x to 4.x

4.x replaces direct executor customization with scheduler-based async running and tightens the action run contract for custom actions.

Most tests built from `Container`, `Parallel`, `Direct`, and `Noop` only need dependency updates. Code that supplied executors, read executors from `Context`, or implemented custom actions needs API changes.

## Dependency update

### Maven

```xml
<properties>
    <paramixel.version>4.0.0</paramixel.version>
</properties>
```

### Gradle

The Paramixel Gradle plugin has been removed. Use `Runner.main()` with a Gradle `JavaExec` task instead:

```groovy
plugins {
    id 'java'
}

dependencies {
    testImplementation 'org.paramixel:core:4.0.0'
}

tasks.register('paramixelTest', JavaExec) {
    dependsOn 'testClasses'
    group = 'verification'
    mainClass = 'org.paramixel.core.Runner'
    classpath = sourceSets.test.runtimeClasspath
}
```

Configure via JVM system properties: `-Dparamixel.parallelism=8`, `-Dparamixel.match.tag=smoke`, etc.

See [Gradle](usage/gradle.md) for details.

## Runner lifecycle

`Runner` now extends `AutoCloseable`. Prefer try-with-resources so listeners that hold resources are closed reliably.

### Before (3.x)

```java
Runner runner = Runner.builder().build();
Result result = runner.run(action);
```

### After (4.x)

```java
try (Runner runner = Runner.builder().build()) {
    Result result = runner.run(action);
}
```

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

## Context async execution

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

## Parallel scheduling

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

## Deadlock detection removed

3.x guidance about nested `Parallel` deadlock detection and custom executor workarounds no longer applies.

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

## Discovery priority

4.x adds `@Paramixel.Priority` for ordering discovered factory classes before `Resolver` builds the resolver-created root `Parallel`.

```java
@Paramixel.Priority(10)
public class SlowDatabaseTests {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Container.builder("SlowDatabaseTests")
                .child(testCreateDatabase())
                .child(testMigrateDatabase())
                .build();
    }
}
```

Priority defaults to `0`. Higher values are ordered earlier, and negative values are allowed.

When multiple classes are discovered, `Resolver` orders returned root actions by:

1. priority descending
2. package name ascending
3. action name ascending
4. class name ascending

Priority changes child admission order for the resolver-created root `Parallel`; it does not guarantee completion order.

## Report format configuration

`paramixel.report.format` has been removed in 4.x. Select the report format with the `paramixel.report.file` extension:

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

Supported inferred formats are text, JSON, XML, and HTML.

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

Null values are not supported by `Store` — the same contract as `Value.of()` which rejected null.

## Ancestor navigation path-based API

`findAncestor(int levelUp)` is replaced by `findAncestor(String path)`. The new path-based API uses path-like semantics for navigating the context hierarchy.

### Before (3.x)

```java
context.findAncestor(0)  // current context
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

## New exception classes

All in `org.paramixel.core.exception`:

| Class | Description |
|---|---|
| `AncestorNotFoundException` | Ancestor context does not exist |

## Migration checklist

- Update dependencies and plugins to `4.0.0`.
- Wrap manually-created runners in try-with-resources.
- Remove `Runner.Builder.executorService(...)` calls.
- Replace `Context.getExecutorService()` usage with `Context.runAsync(action)` for Paramixel actions.
- Replace `Parallel.Builder.executorService(...)` with `Parallel.Builder.scheduler(...)` only when custom scheduling is still needed.
- Remove nested `Parallel` deadlock workaround code that only existed to provide separate executors.
- Review custom actions for explicit listener callbacks, result construction, and context scoping.
- Use `@Paramixel.Priority` if discovered root admission order matters.
- Remove all `paramixel.report.format` usage — report format is now inferred from `paramixel.report.file` extension.
- Remove all `Value.of()` wrapping from `Store.put()` and `Store.putIfAbsent()` calls.
- Replace `.get(key).orElseThrow().cast(T.class)` with `.get(key, T.class).orElseThrow()`.
- Replace `.remove(key).orElseThrow().cast(T.class)` with `.remove(key, T.class).orElseThrow()`.
- Replace `Value.isType()` checks with `Store.isType(key, type)`.
- Replace `findAncestor(N)` with `getAncestor(path)` using `../` per parent hop (e.g., `findAncestor(2)` → `getAncestor("../../")`).
- Replace `context.getParent().orElseThrow()` with `context.getParent()` (now returns Context directly, throws `AncestorNotFoundException` at root).
- Use `findParent()` and `findAncestor(path)` for safe navigation when the ancestor might not exist.
- Update `compute`/`merge`/`replaceAll` lambdas to use raw `Object` types instead of `Value`.
