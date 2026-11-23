---
title: Action
description: The sealed interface for all Paramixel action types.
---

# Action

`Action` is a sealed interface representing an immutable, reusable execution unit processed by a `Runner`.

```java
public sealed interface Action
        permits Assert, Conditional, Delay, Instance, Isolated, Loop, Parallel, Repeat, Scope, Sequence, Sequential,
                Static, Step, Timeout, Until {

    String displayName();
}
```

## Design

An `Action` defines structure and identity, not execution logic. The runner and its execution engine own all descriptor traversal, status transitions, and listener callbacks. Actions carry only their display name; execution behavior is handled by the engine interpreting each sealed subtype.

All action subtypes are immutable after construction. For configurable actions, use the corresponding `Builder` subtype to produce an immutable action snapshot via `Builder.build()`.

## Subtypes

| Type | Description |
|------|-------------|
| `Step` | Terminal action wrapping a `ContextConsumer`. |
| `Assert` | Terminal action evaluating a boolean condition. |
| `Sequential` | Composite: executes children sequentially. Dependent/independent modes. |
| `Sequence` | Composite: executes children sequentially. Same semantics as `Sequential`. _(Deprecated since 6.2 — use `Sequential` instead.)_ |
| `Parallel` | Composite: executes children concurrently with bounded admission. |
| `Scope` | Composite: before-body-after with lifecycle guarantee (after always runs once before has been attempted). |
| `Static` | Composite: before-body-after without a fixture instance. _(Deprecated since 6.2 — use `Scope` instead.)_ |
| `Instance` | Composite: creates a fixture instance, runs body, destroys instance (destroy always runs once creation has been attempted). |
| `Isolated` | Composite: executes body under a named re-entrant lock. Compose with Instance for state isolation. |
| `Delay` | Terminal: pauses for a duration. |
| `Repeat` | Decorator: executes a child action N times. _(Deprecated since 6.2 — use `Loop` instead.)_ |
| `Loop` | Decorator: executes a child action up to `maxIterations` times, optionally until a predicate is satisfied, with an optional inter-iteration delay. Individual iterations may fail without stopping the loop; only an aborted iteration stops it. |
| `Timeout` | Decorator: executes a child action with a wall-clock deadline; on timeout the child is cancelled but started lifecycle cleanup still runs. |
| `Conditional` | Composite: evaluates a `Predicate<Context>`; runs body when true, passes (body subtree skipped) when false. Does not disrupt dependent sibling execution. |
| `Until` | Decorator: executes child repeatedly until predicate satisfied, body passes, or iterations exhausted. _(Deprecated since 6.2 — use `Loop` instead.)_ |

## Composition

Build action trees by nesting actions through builders:

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

See [Builder](builder) for the `Builder` sealed interface and its subtypes.

## Named builders

Most composite actions provide a public static method named after the action
(e.g., `Scope.scope(name)`) that returns the corresponding `Builder`. Import
these statically for concise action tree definitions.

Terminal actions `Step` and `Delay` also provide named factory methods
(`step(name, consumer)`, `delay(name, ms)`, `delayRandom(name, min, max)`) that
return the action directly.

For details and usage examples, see [Named Builders](named-builders).
