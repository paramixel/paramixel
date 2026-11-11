---
title: Built-in Actions and When to Use Them
description: Built-in action types, their roles, and composition examples.
---

# Built-in Actions and When to Use Them

`Action` is a sealed interface in `org.paramixel.api.action` with 8 permitted subtypes: `Step`, `Sequential`, `Parallel`, `Scope`, `Loop`, `Timeout`, `Isolated` and `Conditional`. Each action is an immutable, reusable definition. The runner and scheduler own all traversal, execution, status transitions, and listener callbacks.

```java
public sealed interface Action
        permits Conditional, Isolated, Loop, Parallel, Scope, Sequential, Step, Timeout {

    String displayName();
}
```

## Built-in actions

- `Step` wraps a `ContextConsumer` for user logic.
- `Sequential` runs children one after another in dependent or independent mode, optionally shuffled. In dependent mode (the default), a `FAILED` or `SKIPPED` child short-circuits the sequence (remaining children are skipped); an `ABORTED` child does **not** short-circuit, so siblings still run and the sequence aggregates to `ABORTED`. In independent mode, all children run regardless of outcome. See [Exception Reference](../api/exception-reference) for how each outcome exception affects siblings.
- `Parallel` runs children concurrently, bounded by the named pool given to `parallel(name, pool)`, optionally shuffled.
- `Scope` provides before-body-after execution with lifecycle guarantee: once the before action has been attempted, the after action always runs, regardless of earlier outcomes. A scope cancelled before it starts runs neither.
- `Instance` is a builder, not an action type: `Instance.of(name, type, factory).before(...).body(...).after(...)` builds a `Scope` that creates a typed fixture lazily in its before phase, runs the body, and closes the fixture in its after phase. The `Scope` guarantee applies: once creation has been attempted, destruction always runs.
- `Loop` repeats its body up to `maxIterations`, with an optional `until` predicate (`until(BiPredicate<Context, Status>)` or `untilPassed()`) and an optional `Backoff` delay between iterations.
- `Isolated` executes its body under a named re-entrant lock. Same-named nodes serialize. Nested same-name nodes are re-entrant.
- `Timeout` executes a child action with a wall-clock deadline. On timeout the child is cancelled, but lifecycle cleanup still runs: see [Timeouts and cleanup](#timeouts-and-cleanup).
- `Conditional` evaluates a runtime predicate and gates body execution. When the predicate is false, the node reports `PASSED` and the body subtree is skipped. Because the node passes, it does not disrupt dependent siblings in `Sequential`.

## Choosing an action

| Need | Use |
| --- | --- |
| Run one piece of user code | `Step` |
| Run children in order | `Sequential` |
| Run independent branches concurrently | `Parallel` |
| Attach setup and teardown to a subtree | `Scope` |
| Create and use a fixture instance | `Instance` |
| Serialize access to a shared resource | `Isolated` |
| Retry or repeat a child | `Loop` |
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

### Timeouts and cleanup

When a `Timeout` deadline passes, its child subtree is cancelled: running leaf actions are interrupted and actions that have not started are marked `ABORTED`. Lifecycle cleanup is preserved:

- A `Scope` (including one built by `Instance.of(...)`) whose before action had been attempted still runs its after action, even if the before action or the body was interrupted. An after action that is already running is not interrupted.
- A lifecycle that had not started when the deadline passed runs neither its before nor its after action.
- The `Timeout` completes only after that cleanup has finished, so teardown such as stopping containers or removing networks never outlives the `Timeout`. Cleanup is not bounded by the deadline.
- If cancelled work ignores interruption and the subtree makes no cleanup progress for five seconds, the `Timeout` stops waiting, marks the remaining actions `ABORTED`, and completes.

Use the corresponding `Builder` nested class for each action type. See [Builder](../api/builder) for details.

For a more concise syntax, see the [Named Builders](../api/named-builders) reference.
