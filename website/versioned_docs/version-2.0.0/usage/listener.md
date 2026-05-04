---
title: Listener
description: Observe Paramixel execution.
---

# Listener

`Listener` receives execution callbacks.

## Callbacks

```java
default void runStarted(Runner runner)
default void beforeAction(Result result)
default void actionThrowable(Result result, Throwable throwable)
default void afterAction(Result result)
default void skipAction(Result result)
default void runCompleted(Runner runner, Result result)
```

All callbacks receive a `Result` (which wraps `Action`, `Status`, and timing information), not `Context` + `Action` separately. Use `result.getAction()` to access the action, `result.getStatus()` for the status, and `result.getElapsedTime()` for timing.

The `skipAction(Result)` callback fires for every action that is skipped (either explicitly via `SkipException` or because a parent determined it should not run).

## Built-in listener factory

```java
Factory.defaultListener()
```

`Factory.defaultListener()` combines `StatusListener` (per-action status lines) with `SummaryListener` using `TreeSummaryRenderer` (tree-style run summary), wrapped in `SafeListener`.

There is no separate `treeListener()` — `Factory.defaultListener()` is the standard entry point.

## Safe listener wrapper

`org.paramixel.core.spi.listener.SafeListener` wraps another listener and catches listener-thrown exceptions so they do not break execution. `Error` subclasses (such as `OutOfMemoryError` and `StackOverflowError`) are rethrown immediately rather than caught and logged.

## Custom listener example

```java
Listener listener = new Listener() {
    @Override
    public void beforeAction(Result result) {
        System.out.println("starting " + result.getAction().getName());
    }
};

Runner runner = Runner.builder().listener(listener).build();
```