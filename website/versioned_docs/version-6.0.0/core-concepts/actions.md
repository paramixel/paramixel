---
title: Actions
description: Built-in action types and composition.
---

# Actions

`Action` is a sealed interface in `org.paramixel.api.action` with 10 permitted subtypes. Each action is an immutable, reusable definition. The runner and scheduler own all traversal, execution, status transitions, and listener callbacks.

```java
public sealed interface Action
        permits Assert, Delay, Instance, Parallel, Repeat, Scope, Sequence, Static, Step, Timeout {

    String displayName();
}
```

## Built-in actions

- `Step` wraps a `ContextConsumer` for user logic.
- `Sequence` runs children one after another in dependent or independent mode.
- `Parallel` runs children concurrently with configurable parallelism.
- `Scope` provides before-body-after execution with lifecycle guarantee (after always runs).
- `Static` provides before-body-after without a fixture instance.
- `Instance` creates a fixture instance, runs the body, and destroys it.
- `Assert` evaluates a boolean condition against an expected value.
- `Delay` pauses execution for a duration.
- `Repeat` executes a child action a configurable number of times.
- `Timeout` executes a child action with a wall-clock deadline.

## Composition example

```java
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

Action test = Scope.builder("browser test")
        .before(Step.of("start browser", ctx -> startBrowser()))
        .body(Sequence.builder("scenario")
                .child(Step.of("open page", ctx -> openPage()))
                .child(Step.of("verify", ctx -> verifyPage()))
                .build())
        .after(Step.of("stop browser", ctx -> stopBrowser()))
        .build();
```

Use the corresponding `Builder` nested class for each action type. See [Builder](../api/builder) for details.
