---
title: Built-in Actions and When to Use Them
description: Built-in action types, their roles, and composition examples.
---

# Built-in Actions and When to Use Them

`Action` is a sealed interface in `org.paramixel.api.action` with 13 permitted subtypes. Each action is an immutable, reusable definition. The runner and scheduler own all traversal, execution, status transitions, and listener callbacks.

```java
public sealed interface Action
        permits Assert, Conditional, Delay, Instance, Isolated, Parallel, Repeat, Scope, Sequence, Static, Step, Timeout, Until {

    String displayName();
}
```

## Built-in actions

- `Step` wraps a `ContextConsumer` for user logic.
- `Sequence` runs children one after another in dependent or independent mode, optionally shuffled.
- `Parallel` runs children concurrently with configurable parallelism, optionally shuffled.
- `Scope` provides before-body-after execution with lifecycle guarantee; the after action always runs.
- `Static` provides before-body-after without a fixture instance.
- `Instance` creates a fixture instance, runs the body, and destroys it.
- `Assert` evaluates a boolean condition against an expected value. Passes when the actual value equals the expected value; fails with `FailException` otherwise. Created via static factories, not a builder: `Assert.of(name, expected, actual)`, `Assert.of(name, expected, () -> compute(), "message")`.
- `Delay` pauses execution for a fixed or random duration. Created via static factories: `Delay.of(name, millis)` or `Delay.of(name, Duration)`.
- `Isolated` executes its body under a named re-entrant lock. Same-named nodes serialize. Nested same-name nodes are re-entrant.
- `Repeat` executes a child action a configurable number of times.
- `Timeout` executes a child action with a wall-clock deadline.
- `Conditional` evaluates a runtime predicate and gates body execution. False predicates skip the body subtree.
- `Until` executes a child action repeatedly until a `Predicate<Context>` returns `true`, the body action passes, or `maxIterations` is exhausted. Individual iteration failures continue the loop. When `until()` is configured, only `ABORTED` or predicate satisfaction stops early; exhaustion reports `FAILED`. When `until()` is absent, the loop stops when the body passes and reports `PASSED`.

## Choosing an action

| Need | Use |
| --- | --- |
| Run one piece of user code | `Step` |
| Check a boolean condition | `Assert` |
| Run children in order | `Sequence` |
| Run independent branches concurrently | `Parallel` |
| Attach setup and teardown to a subtree | `Scope` or `Static` |
| Create and use a fixture instance | `Instance` |
| Serialize access to a shared resource | `Isolated` |
| Retry or repeat a child | `Repeat` or `Until` |
| Bound a child by elapsed time | `Timeout` |
| Skip a subtree based on runtime state | `Conditional` |

## Composition example

```java
import org.paramixel.api.action.Action;
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

```text
browser test
├── before: start browser
├── body: scenario
│   ├── open page
│   └── verify
└── after: stop browser
```

Use `Scope` when setup and teardown belong to a specific action tree. Use [`@Paramixel.BeforeAll` and `@Paramixel.AfterAll`](discovery#beforeall-and-afterall-hooks) only for runner-wide lifecycle around all discovered factory actions.

Use the corresponding `Builder` nested class for each action type. See [Builder](../api/builder) for details.
