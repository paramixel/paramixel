---
title: Builder Variants
description: Sequential, parallel, and repetition builder views.
---

# Builder Variants

`Tasks` returns one of three builder views. The view determines which policies compile.

| Builder | Created by | Extra methods |
| --- | --- | --- |
| `SequentialBuilder` | `Tasks.sequential(name)`, `Tasks.sequential(Owner.class, name)`, `sequential(...)` | `dependent()`, `independent()` |
| `ParallelBuilder` | `Tasks.parallel(name, pool)`, `Tasks.parallel(Owner.class, name, pool)` | none — parallel groups have no continuation policy |
| `RepeatBuilder` | `Tasks.repeat(name)`, `Tasks.repeat(Owner.class, name)` | `maxIterations`, `untilPassed`, `until(id)`, `delay` |

## Sequential

```java
sequential(MySuite.class, "Validation")
    .independent()
    .step("checkA")
    .step("checkB");
```

If `Check A` fails and `Check B` passes, both run and the group is `FAILED`.

## Parallel

```java
final var matrix = parallel(MySuite.class, "Environments", Pool.named("environments"));
for (final var environment : environments) {
    matrix.child(sequential(environment.name()).bind(ENVIRONMENT, environment).step("verify"));
}
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

`maxIterations` is mandatory. Each iteration is a distinct attempt in the result tree, and an owned `instance` task inside
the repeated subtree is re-instantiated per iteration.
