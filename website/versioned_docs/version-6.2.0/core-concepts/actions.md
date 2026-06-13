---
title: Built-in Actions and When to Use Them
description: Built-in action types, their roles, and composition examples.
---

# Built-in Actions and When to Use Them

`Action` is a sealed interface in `org.paramixel.api.action` with 13 permitted subtypes. Each action is an immutable, reusable definition. The runner and scheduler own all traversal, execution, status transitions, and listener callbacks.

```java
public sealed interface Action
        permits Assert, Conditional, Delay, Instance, Isolated, Parallel, Repeat, Scope, Sequential, Static, Step, Timeout, Until {

    String displayName();
}
```

## Built-in actions

- `Step` wraps a `ContextConsumer` for user logic.
- `Sequential` runs children one after another in dependent or independent mode, optionally shuffled.
- `Parallel` runs children concurrently with configurable parallelism, optionally shuffled.
- `Scope` provides before-body-after execution with lifecycle guarantee; the after action always runs.
- `Static` provides before-body-after without a fixture instance. _(Deprecated since 6.2 — use `Scope` instead.)_
- `Instance` creates a fixture instance, runs the body, and destroys it.
- `Assert` evaluates a boolean condition against an expected value. Passes when the actual value equals the expected value; fails with `FailException` otherwise. Created via static factories, not a builder: `assertTrue(name, actual)`, `assertFalse(name, actual)`, `assertThat(name, expected, actual)`, `assertThat(name, expected, () -> compute(), "message")`.
- `Delay` pauses execution for a fixed or random duration. Created via static factories: `delay(name, millis)` or `delay(name, Duration)`.
- `Isolated` executes its body under a named re-entrant lock. Same-named nodes serialize. Nested same-name nodes are re-entrant.
- `Repeat` executes a child action a configurable number of times.
- `Timeout` executes a child action with a wall-clock deadline.
- `Conditional` evaluates a runtime predicate and gates body execution. When the predicate is false, the node reports `PASSED` and the body subtree is skipped. Because the node passes, it does not disrupt dependent siblings in `Sequential`.
- `Until` executes a child action repeatedly until a `Predicate<Context>` returns `true`, the body action passes, or `maxIterations` is exhausted. Individual iteration failures continue the loop. When `until()` is configured, only `ABORTED` or predicate satisfaction stops early; exhaustion reports `FAILED`. When `until()` is absent, the loop stops when the body passes and reports `PASSED`.

## Choosing an action

| Need | Use |
| --- | --- |
| Run one piece of user code | `Step` |
| Check a boolean condition | `Assert` |
| Run children in order | `Sequential` |
| Run independent branches concurrently | `Parallel` |
| Attach setup and teardown to a subtree | `Scope` _(was `Static` — deprecated since 6.2)_ |
| Create and use a fixture instance | `Instance` |
| Serialize access to a shared resource | `Isolated` |
| Retry or repeat a child | `Repeat` or `Until` |
| Bound a child by elapsed time | `Timeout` |
| Gate a subtree based on runtime state | `Conditional` |

## Composition example

```java
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

Action test = scope("browser test")
        .before(step("start browser", ctx -> startBrowser()))
        .body(sequential("scenario")
                .child(step("open page", ctx -> openPage()))
                .child(step("verify", ctx -> verifyPage()))
                .build())
        .after(step("stop browser", ctx -> stopBrowser()))
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

For a more concise syntax, see the [Named Builders](../api/named-builders) reference.
