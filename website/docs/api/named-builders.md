---
title: Builder Variants
description: Sequential, parallel, and repetition builder views.
---

# Builder Variants

`Tasks` returns one of three builder views. The view determines which policies compile.

| Builder | Created by | Extra methods |
| --- | --- | --- |
| `SequentialBuilder<T>` | `Tasks.task`, `Tasks.sequential`, `Tasks.forInstance`, `sequential(...)`, `step(..., children -> ...)` | `dependent()`, `independent()` |
| `ParallelBuilder<T>` | `Tasks.parallel`, `parallel(...)` | none — parallel groups have no continuation policy |
| `RepeatBuilder<T>` | `Tasks.repeat`, `repeat(...)` | `maxIterations`, `untilPassed`, `until`, `delay` |

## Sequential

```java
sequential("Validation", tests -> tests
    .independent()
    .step("Check A", Test::checkA)
    .step("Check B", Test::checkB));
```

If `Check A` fails and `Check B` passes, both run and the group is `FAILED`.

## Parallel

```java
parallel("Environments", Pool.named("environments"), matrix -> matrix
    .each(environments, env -> forInstance(env.name(), () -> new Probe(env))
            .step("Verify", Probe::verify)));
```

Every direct child holds one permit of the named pool for its entire lifetime.

## Repetition

```java
repeat("Poll readiness", poll -> poll
    .maxIterations(5)
    .untilPassed()
    .delay(Backoff.fixed(Duration.ofMillis(250)))
    .step("Probe", Probe::assertReady));
```

`maxIterations` is mandatory. Each iteration is a distinct attempt in the result tree, and an owned `forInstance` inside
the repeated subtree is re-instantiated per iteration.
