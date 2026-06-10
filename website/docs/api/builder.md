---
title: Builder
description: Mutable builders that produce immutable Action snapshots.
---

# Builder

`Builder` is a sealed interface for constructing immutable `Action` instances.

```java
public sealed interface Builder
        permits Conditional.Builder,
                Instance.Builder,
                Isolated.Builder,
                Parallel.Builder,
                Repeat.Builder,
                Scope.Builder,
                Sequential.Builder,
                Static.Builder, // Deprecated since 6.2 — use Scope.Builder instead
                Timeout.Builder,
                Until.Builder {

    Action build();
}
```

## Design

Each `Action` subtype that requires configuration exposes a corresponding `Builder` nested class. Callers configure the builder, then call `build()` to produce an immutable action snapshot. Builders are reusable — `build()` may be called multiple times.

## Builder subtypes

| Builder | Produces | Description |
|---------|----------|-------------|
| `Sequential.Builder` | `Sequential` | Sequential execution; `.child(action)` adds children, `.dependent()`/`.independent()` controls failure propagation. |
| `Parallel.Builder` | `Parallel` | Concurrent execution; `.parallelism(int)` controls in-flight count. |
| `Scope.Builder` | `Scope` | Before-body-after with lifecycle guarantee. |
| `Static.Builder` | `Static` | Before-body-after without fixture instance. _(Deprecated since 6.2 — use `Scope.Builder` instead.)_ |
| `Instance.Builder` | `Instance` | Fixture instance lifecycle; `.body(action)` sets the body. |
| `Isolated.Builder` | `Isolated` | Named-lock execution; `.body(action)` sets the protected child. |
| `Repeat.Builder` | `Repeat` | Executes child action N times. |
| `Timeout.Builder` | `Timeout` | Executes child action with wall-clock deadline. |
| `Conditional.Builder` | `Conditional` | Evaluates a runtime predicate and gates body execution. |
| `Until.Builder` | `Until` | Executes child repeatedly until predicate satisfied, body passes, or iterations exhausted; `.until(Predicate)` optional, rejects `null`. Without `.until()`, the loop stops when the body passes and reports `PASSED`. |


## Named builder entry points
For common configurations that need quick action tree definitions, each composite action
provides a static method named after the action (e.g.,
`Scope.scope(name)`, `Parallel.parallel(name)`) that returns its `Builder`.
Import these statically as a shorthand. See [Named Builders](named-builders).

Terminal actions `Step` and `Delay` provide named factory methods
(`step(name, consumer)`, `delay(name, ms)`, `delayRandom(name, min, max)`) that
return the action directly, also suitable for static imports.
## Usage

```java
Action action = Sequential.sequential("flow")
        .child(Step.of("step one", ctx -> stepOne()))
        .child(Step.of("step two", ctx -> stepTwo()))
        .build();

var result = Runner.defaultRunner().run(action);
```

Each action factory method returns a `Builder`:

```java
Action scoped = Scope.scope("browser test")
        .before(Step.of("start", ctx -> start()))
        .body(Step.of("test", ctx -> test()))
        .after(Step.of("stop", ctx -> stop()))
        .build();

Action fixture = Instance.instance("user test", UserFixture::new)
        .body(Step.of("verify", Context.withInstance(UserFixture.class, f -> f.verify())))
        .build();
```

`Sequential.Builder` supports dependent/independent failure propagation:

```java
var dependent = Sequential.sequential("flow")
        .dependent()
        .child(failingStep)
        .child(skippedStep)
        .build();

var independent = Sequential.sequential("flow")
        .independent()
        .child(failingStep)
        .child(stillRunsStep)
        .build();
```

## Shuffled execution

Both `Sequential.Builder` and `Parallel.Builder` support randomizing child execution order with `.shuffle()`:

```java
var seq = Sequential.sequential("checkout")
        .shuffle()
        .child(Step.of("add to cart", ctx -> addToCart()))
        .child(Step.of("checkout", ctx -> checkout()))
        .child(Step.of("verify", ctx -> verify()))
        .build();
```

`.shuffle()` generates a seed from `ThreadLocalRandom` at build time. For reproducible ordering — essential when investigating flaky order-dependent failures — use `.shuffle(long seed)`:

```java
var seq = Sequential.sequential("checkout")
        .shuffle(42L)
        .child(Step.of("add to cart", ctx -> addToCart()))
        .child(Step.of("checkout", ctx -> checkout()))
        .child(Step.of("verify", ctx -> verify()))
        .build();
```

The stored seed is accessible via `Sequential.seed()` and `Parallel.seed()`. When `.shuffle()` is not called, `isShuffled()` returns `false` and children execute in insertion order.

See [Action](action) for the `Action` sealed interface and its subtypes.
