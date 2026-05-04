---
title: Migration Guide
description: Migrate from Paramixel 1.x to 2.0.0.
---

# Migration Guide

This guide covers the breaking changes between Paramixel 1.x and 2.0.0 and how to update your code.

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

## StrictSequential → DependentSequential

### Before (1.x)

```java
import org.paramixel.core.action.StrictSequential;
Action action = StrictSequential.of("suite", ...);
```

### After (2.0.0)

```java
import org.paramixel.core.action.DependentSequential;
Action action = DependentSequential.of("suite", ...);
```

Same semantics, new name. `StrictRandomSequential` → `DependentRandomSequential` follows the same pattern.

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
Duration getElapsedTime()
Duration getCumulativeElapsedTime()
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