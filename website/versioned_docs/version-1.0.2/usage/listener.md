---
title: Listener
description: Observe Paramixel execution.
---

# Listener

`Listener` receives execution callbacks.

## Callbacks

```java
runStarted(Runner runner, Action action)
runCompleted(Runner runner, Action action)
beforeAction(Context context, Action action)
actionThrowable(Context context, Action action, Throwable throwable)
afterAction(Context context, Action action, Result result)
```

## Built-in listener factories

```java
Listener.defaultListener()
Listener.treeListener()
```

Current behavior:

- `defaultListener()` combines status output with table summary output
- `treeListener()` combines status output with tree summary output

These are implemented with classes in `org.paramixel.core.listener`, including `CompositeListener`, `StatusListener`, `SummaryListener`, `TableSummaryRenderer`, and `TreeSummaryRenderer`.

## Safe listener wrapper

`org.paramixel.core.listener.SafeListener` wraps another listener and catches listener-thrown exceptions so they do not break execution. `Error` subclasses (such as `OutOfMemoryError` and `StackOverflowError`) are rethrown immediately rather than caught and logged.

## Custom listener example

```java
Listener listener = new Listener() {
    @Override
    public void beforeAction(Context context, Action action) {
        System.out.println("starting " + action.getName());
    }
};

Runner runner = Runner.builder().listener(listener).build();
```
