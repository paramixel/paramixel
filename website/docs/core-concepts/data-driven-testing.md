---
title: Data-Driven Testing
description: Typed matrices with each, without casts or repeated class tokens.
---

# Data-Driven Testing

`each(items, mapper)` expands items into children **eagerly at definition time**. The mapper returns a builder, so every
item can produce a different fixture type.

```java
Task definition = parallel("Environments", Pool.named("environments"))
        .each(environments, environment -> forInstance(environment.name(), () -> new Scenario(environment))
                .before(Scenario::start)
                .step("verify", Scenario::verify)
                .after(Scenario::stop))
        .build();
```

## Rules

- Iteration order is preserved; a set or map with nondeterministic iteration order is the caller's choice and is not
  presented as stable.
- The mapper runs while the definition is authored. It may capture application objects, but must not run test bodies,
  create fixtures, or acquire runtime resources.
- A `null` mapping fails the definition immediately.
- Heterogeneous outputs are allowed: one item may produce a builder bound to `A` and another a builder bound to `B`,
  with no casts.

## Shuffling

```java
parallel("Matrix", Pool.named("environments"))
        .shuffle(42L)
        .each(environments, env -> forInstance(env.name(), () -> new Probe(env))
                .step("verify", Probe::verify))
        .build();
```

Shuffling reorders admission for the task's direct children. The effective seed and resolved order are recorded in the
result (`shuffleSeed()`, `executionOrder()`), and the same definition plus seed always yields the same order.

## Sharing state

Each item owns its own instance. Cross-item mutable state in a static field is shared and unsynchronized; if items run in
parallel, guard it or use `isolated`.
