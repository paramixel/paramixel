---
title: Builder
description: Mutable builders that produce immutable Action snapshots.
---

# Builder

`ActionBuilder` is a sealed interface: anything that can produce an immutable `Action`. A built `Action` is its own
builder, so an action and a builder can be passed interchangeably wherever a child, body, before or after is
accepted.

```java
public sealed interface ActionBuilder
        permits Action,
                Conditional.Builder,
                Instance.Builder,
                Isolated.Builder,
                Loop.Builder,
                Parallel.Builder,
                Scope.Builder,
                Sequential.Builder,
                Timeout.Builder {

    Action build();
}
```

## Design

Each `Action` subtype that needs configuration exposes a `Builder`. Configure it, then call `build()` for an
immutable snapshot. Builders are reusable: `build()` may be called multiple times and produces a new snapshot each
time. Builder inputs are built when they are accepted, not retained for deferred building.

## Builder subtypes

| Builder | Produces | Description |
|---------|----------|-------------|
| `Sequential.Builder` | `Sequential` | Sequential execution; `child`, `children`, `each`, `dependent`/`independent`, `shuffle`. |
| `Parallel.Builder` | `Parallel` | Concurrent execution bounded by the named pool given to `parallel(name, pool)`; `child`, `children`, `each`, `shuffle`. |
| `Scope.Builder` | `Scope` | `before`/`body`/`after` with lifecycle guarantees. |
| `Instance.Builder<T>` | `Scope` | A typed fixture: `of(name, type, factory)`, `before(name, consumer)`, `body`, `after(name, consumer)`. |
| `Loop.Builder` | `Loop` | Bounded repetition with `until`, `untilPassed` and a `Backoff` delay. |
| `Isolated.Builder` | `Isolated` | Named-lock execution; `body(action)` sets the protected body. |
| `Timeout.Builder` | `Timeout` | Runs its body under a wall-clock deadline. |
| `Conditional.Builder` | `Conditional` | Evaluates a runtime predicate and gates body execution. |

## Named builder entry points

Every composite action provides a static method named after the action that returns its `Builder`:
`scope(name)`, `sequential(name)`, `parallel(name, pool)`, `instance`… replaced by `Instance.of(...)`,
`loop(name)`, `timeout(name)`, `isolated(name, lock)`, `conditional(name, predicate)`.

`Step` provides `step(name, consumer)` and `step(name, type, consumer)` and returns the action directly. Both forms
are convenient to import statically. See [Named Builders](named-builders).

## Usage

```java
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

Action action = sequential("flow")
        .child(step("step one", context -> stepOne()))
        .child(step("step two", context -> stepTwo()))
        .build();

var result = Runner.builder().build().run(action);
```

A fixture is a typed builder rather than a nested scope:

```java
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Step.step;

Action fixture = of("user test", UserFixture.class, UserFixture::new)
        .before("start", UserFixture::start)
        .body(step("verify", UserFixture.class, UserFixture::verify))
        .after("stop", UserFixture::stop)
        .build();
```
