---
title: Builder
description: Mutable builders that produce immutable Action snapshots.
---

# Builder

`Builder` is a sealed interface for constructing immutable `Action` instances.

```java
public sealed interface Builder
        permits Instance.Builder,
                Parallel.Builder,
                Repeat.Builder,
                Scope.Builder,
                Sequence.Builder,
                Static.Builder,
                Timeout.Builder {

    Action build();
}
```

## Design

Each `Action` subtype that requires configuration exposes a corresponding `Builder` nested class. Callers configure the builder, then call `build()` to produce an immutable action snapshot. Builders are reusable — `build()` may be called multiple times.

## Builder subtypes

| Builder | Produces | Description |
|---------|----------|-------------|
| `Sequence.Builder` | `Sequence` | Sequential execution; `.child(action)` adds children. |
| `Parallel.Builder` | `Parallel` | Concurrent execution; `.parallelism(int)` controls in-flight count. |
| `Scope.Builder` | `Scope` | Before-body-after with lifecycle guarantee. |
| `Static.Builder` | `Static` | Before-body-after without fixture instance. |
| `Instance.Builder` | `Instance` | Fixture instance lifecycle; `.body(action)` sets the body. |
| `Repeat.Builder` | `Repeat` | Executes child action N times. |
| `Timeout.Builder` | `Timeout` | Executes child action with wall-clock deadline. |

## Usage

```java
Action action = Sequence.builder("flow")
        .child(Step.of("step one", ctx -> stepOne()))
        .child(Step.of("step two", ctx -> stepTwo()))
        .build();

var result = Runner.defaultRunner().run(action);
```

Each action factory method returns a `Builder`:

```java
Action scoped = Scope.builder("browser test")
        .before(Step.of("start", ctx -> start()))
        .body(Step.of("test", ctx -> test()))
        .after(Step.of("stop", ctx -> stop()))
        .build();

Action fixture = Instance.builder("user test", UserFixture::new)
        .body(Step.of("verify", Context.withInstance(UserFixture.class, f -> f.verify())))
        .build();
```

See [Action](action) for the `Action` sealed interface and its subtypes.
