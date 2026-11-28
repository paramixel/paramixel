---
title: Builder Variants
description: Sequential, parallel, and repetition builder views.
---

# Builder Variants

Each node kind is a public type with static `builder` factories. The builder decides which policies compile, and
`build()` returns the node type.

| Node | Builder | Created by | Extra policy methods |
| --- | --- | --- | --- |
| `Sequential` | `SequentialBuilder` | `Sequential.builder(name)`, `Sequential.builder(Owner.class, name)` | `dependent()`, `independent()` |
| `Parallel` | `ParallelBuilder` | `Parallel.builder(name, pool)`, `Parallel.builder(Owner.class, name, pool)` | none — parallel groups have no continuation policy |
| `Repeat` | `RepeatBuilder` | `Repeat.builder(name)`, `Repeat.builder(Owner.class, name)` | `maxIterations`, `untilPassed`, `until(id)`, `delay` |

`SequentialBuilder.build()` returns `Sequential`, `ParallelBuilder.build()` returns `Parallel`, and
`RepeatBuilder.build()` returns `Repeat`, so a helper method can declare the node type it produces.

## Sequential

```java
Sequential.builder(MySuite.class, "Validation")
    .independent()
    .step("checkA")
    .step("checkB");
```

If `Check A` fails and `Check B` passes, both run and the group is `FAILED`.

## Parallel

```java
final var matrix = Parallel.builder(MySuite.class, "Environments", Pool.named("environments"));
for (final var environment : environments) {
    matrix.step(Sequential.builder(environment.name()).bind(ENVIRONMENT, environment).step("verify"));
}
```

Every direct child holds one permit of the named pool for its entire lifetime.

## Repetition

```java
Repeat.builder(MySuite.class, "readiness")
    .maxIterations(5)
    .untilPassed()
    .delay(Backoff.fixed(Duration.ofMillis(250)))
    .step("probe");
```

`maxIterations` is mandatory. Each iteration is a distinct attempt in the result tree, and a repeated task owns its
context scope, so every iteration starts with an empty scope.
The two `until` overloads set the same predicate. The latest call wins, and a hook ID resolves against the effective
anchor of each build. `untilPassed()` can be combined with either overload.
