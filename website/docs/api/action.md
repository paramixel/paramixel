---
title: Action
description: The sealed interface for all Paramixel action types.
---

# Action

`Action` is a sealed interface representing an immutable, reusable execution unit processed by a `Runner`.

```java
public sealed interface Action
        permits Conditional, Isolated, Loop, Parallel, Scope, Sequential, Step, Timeout {

    String displayName();
}
```

## Design

An `Action` defines structure and identity, not execution logic. The runner and scheduler own all descriptor traversal, status transitions, and listener callbacks. Actions carry only their display name; execution behavior is handled by the scheduler interpreting each sealed subtype.

All action subtypes are immutable after construction. For configurable actions, use the corresponding `Builder` subtype to produce an immutable action snapshot via `Builder.build()`.

## Subtypes

| Type | Description |
|------|-------------|
| `Step` | Terminal action wrapping a `ContextConsumer`, or a typed step that receives the enclosing fixture. |
| `Sequential` | Composite: executes children sequentially. Dependent/independent modes. |
| `Parallel` | Composite: executes children concurrently with bounded admission. |
| `Scope` | Composite: before-body-after with lifecycle guarantee (after always runs once before has been attempted). |
| `Instance` | Builder that produces a `Scope`: creates a typed fixture lazily, runs before/body/after, destroys it (destroy always runs once creation has been attempted). |
| `Isolated` | Composite: executes body under a named re-entrant lock. Compose with Instance for state isolation. |
| `Loop` | Composite: repeats its body up to `maxIterations`, with an optional `until` predicate and a `Backoff` delay. |
| `Timeout` | Decorator: executes its body under a wall-clock deadline; on timeout the body is cancelled but started lifecycle cleanup still runs. |
| `Conditional` | Composite: evaluates a `Predicate<Context>`; runs body when true, passes (body subtree skipped) when false. Does not disrupt dependent sibling execution. |

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

`Step` also provides named factory methods (`step(name, consumer)` and
`step(name, type, consumer)`) that return the action directly.

For details and usage examples, see [Named Builders](named-builders).
