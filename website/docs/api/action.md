---
title: Action
description: The sealed interface for all Paramixel action types.
---

# Action

`Action` is a sealed interface representing an immutable, reusable execution unit processed by a `Runner`.

```java
public sealed interface Action
        permits Assert, Conditional, Delay, Instance, Isolated, Parallel, Repeat, Scope, Sequential, Static, Step, Timeout, Until {

    String displayName();
}
```

## Design

An `Action` defines structure and identity, not execution logic. The runner and scheduler own all descriptor traversal, status transitions, and listener callbacks. Actions carry only their display name; execution behavior is handled by the scheduler interpreting each sealed subtype.

All action subtypes are immutable after construction. For configurable actions, use the corresponding `Builder` subtype to produce an immutable action snapshot via `Builder.build()`.

## Subtypes

| Type | Description |
|------|-------------|
| `Step` | Terminal action wrapping a `ContextConsumer`. |
| `Assert` | Terminal action evaluating a boolean condition. |
| `Sequential` | Composite: executes children sequentially. Dependent/independent modes. |
| `Parallel` | Composite: executes children concurrently with bounded admission. |
| `Scope` | Composite: before-body-after with lifecycle guarantee (after always runs). |
| `Static` | Composite: before-body-after without a fixture instance. _(Deprecated since 6.2 — use `Scope` instead.)_ |
| `Instance` | Composite: creates a fixture instance, runs body, destroys instance. |
| `Isolated` | Composite: executes body under a named re-entrant lock. Compose with Instance for state isolation. |
| `Delay` | Terminal: pauses for a duration. |
| `Repeat` | Decorator: executes a child action N times. |
| `Timeout` | Decorator: executes a child action with a wall-clock deadline. |
| `Conditional` | Composite: evaluates a `Predicate<Context>`; runs body when true, skips entire subtree when false. |
| `Until` | Decorator: executes child repeatedly until predicate satisfied, body passes, or iterations exhausted. |

## Composition

Build action trees by nesting actions through builders:

```java
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

Action test = Scope.scope("browser test")
        .before(Step.of("start browser", ctx -> startBrowser()))
        .body(Sequential.sequential("scenario")
                .child(Step.of("open page", ctx -> openPage()))
                .child(Step.of("verify", ctx -> verifyPage()))
                .build())
        .after(Step.of("stop browser", ctx -> stopBrowser()))
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
