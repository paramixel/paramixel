---
title: Actions
description: Built-in action types and composition.
---

# Actions

`Action` is a sealed interface in `org.paramixel.api.action` with 12 permitted subtypes. Each action is an immutable, reusable definition. The runner and scheduler own all traversal, execution, status transitions, and listener callbacks.

```java
public sealed interface Action
        permits Assert, Conditional, Delay, Instance, Isolated, Parallel, Repeat, Scope, Sequence, Static, Step, Timeout {

    String displayName();
}
```

## Built-in actions

- `Step` wraps a `ContextConsumer` for user logic.
- `Sequence` runs children one after another in dependent or independent mode, optionally shuffled.
- `Parallel` runs children concurrently with configurable parallelism, optionally shuffled.
- `Scope` provides before-body-after execution with lifecycle guarantee (after always runs).
- `Static` provides before-body-after without a fixture instance.
- `Instance` creates a fixture instance, runs the body, and destroys it.
- `Assert` evaluates a boolean condition against an expected value. Passes when the actual value equals the expected value; fails with `FailException` otherwise. Created via static factories, not a builder: `Assert.of(name, expected, actual)`, `Assert.of(name, expected, () -> compute(), "message")`.
- `Delay` pauses execution for a fixed or random duration. Created via static factories: `Delay.of(name, millis)` or `Delay.of(name, Duration)`.
- `Isolated` executes its body under a named re-entrant lock. Same-named nodes serialize. Nested same-name nodes are re-entrant.
- `Repeat` executes a child action a configurable number of times.
- `Timeout` executes a child action with a wall-clock deadline.
- `Conditional` evaluates a runtime predicate and gates body execution. False predicates skip the body subtree.

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

Use `Scope` when setup and teardown belong to a specific action tree. Use [`@Paramixel.BeforeAll` and `@Paramixel.AfterAll`](discovery#beforeall-and-afterall-hooks) only for runner-wide lifecycle around all discovered factory actions.

Use the corresponding `Builder` nested class for each action type. See [Builder](../api/builder) for details.
