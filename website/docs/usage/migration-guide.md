---
title: Migration Guide
description: Migrate from Paramixel 1.x to newer releases.
---

# Migration Guide

This legacy guide covers the breaking changes between Paramixel 1.x and 2.0.0. For 3.x, use [Migration 1.x to 3.x](migration-1-to-3) or [Migration 2.x to 3.x](migration-2-to-3).

## Attachment → Store + Value

The single-attachment model has been replaced with a `Store` (key-value map) + `Value` (typed wrapper).

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

### After (2.0.0)

```java
context.getStore().put("data", Value.of(new MyData("value")));
MyData data = context.getStore()
        .get("data")
        .map(Value::get)
        .map(v -> (MyData) v)
        .orElseThrow();
context.findAncestor(1).orElseThrow().getStore()
        .get("data")
        .map(Value::get)
        .map(v -> (MyData) v)
        .orElseThrow();
context.getStore().remove("data");
```

The `Store` API provides `putIfAbsent`, `compute`, `merge`, `forEach`, and other `ConcurrentMap`-like methods. Each context has its own independent `Store`.

## Strict Sequential Actions

### Before (1.x)

```java
import org.paramixel.core.action.StrictSequential;
Action action = StrictSequential.of("suite", ...);
```

### After (2.0.0)

```java
// In 2.0.0, use the dependent sequential action.
```

In 3.x, use `Container` with its default dependent policy. Strict random execution maps to `Container.Policy` with `OrderMode.SHUFFLED`.

## Listener API

Listener callbacks now receive `Result` instead of `Context` + `Action`. Use `result.getAction()` to access the action.

### Before (1.x)

```java
void beforeAction(Context context, Action action)
void actionThrowable(Context context, Action action, Throwable throwable)
void afterAction(Context context, Action action, Result result)
```

### After (2.0.0)

```java
void beforeAction(Result result)
void actionThrowable(Result result, Throwable throwable)
void afterAction(Result result)
void skipAction(Result result)
```

The `skipAction(Result)` callback is new in 2.0.0 — it fires for every skipped action.

## ConsoleRunner → Runner

`ConsoleRunner` no longer exists. Its methods are now on `Runner`.

### Before (1.x)

```java
ConsoleRunner.runAndExit(action);
int exitCode = ConsoleRunner.runAndReturnExitCode(action);
```

### After (2.0.0)

```java
Runner runner = Runner.builder().build();
runner.runAndExit(action);
int exitCode = runner.runAndReturnExitCode(action);
```

## Result and Status interfaces

`Result` and `Status` are now interfaces.

### Result

Before (1.x): `Result.of(status, elapsedTime)`, `Result.pass(...)`, `Result.fail(...)`, `Result.skip(...)`, `Result.staged()`.
After (2.0.0): Access `Result` through the `Runner.run(Action)` return value. The `Result` interface provides:

```java
Optional<Result> getParent()
List<Result> getChildren()
Action getAction()
Status getStatus()
Duration getRunDuration()
```

### Status

Before (1.x): `Status.pass()`, `Status.failure(...)`, `Status.skip(...)`, `Status.staged()`, `isFail()`.
After (2.0.0): `Status` is an interface. Check status with `isPass()`, `isFailure()`, `isSkip()`. The `isFail()` method is now `isFailure()`.

## Selector builder

`Selector.byPackageName()` and `Selector.byClassName()` are removed. Use `Selector.builder()`:

### Before (1.x)

```java
Selector.byPackageName("com.example.tests");
Selector.byClassName("com.example.tests.MyTest");
```

### After (2.0.0)

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

### After (2.0.0)

```java
context.findAncestor(1);
```

Same semantics, renamed for clarity.

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

### After (2.0.0)

```java
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;
```

New exceptions in `org.paramixel.core.exception`:

- `CycleDetectedException` — thrown by `CycleLoopDetector`
- `DeadlockDetected` — thrown by `DeadlockDetector`

## Factory class

`Factory` provides static utility methods:

```java
Factory.defaultRunner();    // returns a Runner with default configuration and listener
Factory.defaultListener();   // returns SafeListener(StatusListener + SummaryListener(TreeSummaryRenderer))
```

Previously these were accessed via `Listener.defaultListener()` and `Listener.treeListener()`.

## @Paramixel.Tag

New annotation for tagging action factories:

```java
@Paramixel.ActionFactory
@Paramixel.Tag("smoke")
public static Action smokeTests() { /* ... */ }
```

Filter by tag using `Selector.builder().tagMatch("smoke")` or the `paramixel.match.tag` configuration key.

## 2.x to 3.0.0

### Result timing API

`getElapsedTime()` and `getCumulativeElapsedTime()` have been replaced by a single `getRunDuration()` method. `DefaultResult.setElapsedTime()` has been replaced by `setRunDuration()`.

| 2.x | 3.0.0 |
|---|---|
| `Result.getElapsedTime()` | `Result.getRunDuration()` |
| `Result.getCumulativeElapsedTime()` | `Result.getRunDuration()` |
| `DefaultResult.setElapsedTime(Duration)` | `DefaultResult.setRunDuration(Duration)` |
| `DefaultResult(Action, Status, Duration elapsedTime)` | `DefaultResult(Action, Status, Duration runDuration)` |

`getRunDuration()` returns the wall-clock time of the full execute or skip. For leaf results this is the same as the old `getElapsedTime()`. For composed results, this returns the parent's own wall-clock duration, which inherently includes all child execution time. The old `getCumulativeElapsedTime()` summed children's times separately for composed results, which was misleading — consumers who need aggregated child timing should compute it from the result tree.

### JSON and XML report format

The JSON report field `"elapsedTime"` has been renamed to `"runDuration"` and the `"cumulativeTime"` field has been removed. The XML report attribute `elapsedTime` has been renamed to `runDuration` and the `cumulativeTime` attribute has been removed. The HTML report's embedded JSON uses `runDuration` consistently with the standalone JSON and XML reports.
