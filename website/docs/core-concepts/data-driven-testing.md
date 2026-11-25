---
title: Data-Driven Testing
description: Typed matrices with each, without casts or repeated class tokens.
---

# Data-Driven Testing

`each(items, mapper)` expands items into children **eagerly at definition time**. The mapper returns a builder, so every
item can produce a different fixture type.

```java
final var root = parallel(MySuite.class, "Environments", Pool.named("environments"));
for (final var environment : environments) {
    root.child(sequential(environment.name())
            .bind(ENVIRONMENT, environment)
            .before(ID_START)
            .step(ID_VERIFY)
            .after(ID_STOP));
}
Task definition = root.build();
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
final var matrix = parallel(MySuite.class, "Matrix", Pool.named("environments")).shuffle(42L);
for (final var environment : environments) {
    matrix.child(sequential(environment.name()).bind(ENVIRONMENT, environment).step(ID_VERIFY));
}
Task definition = matrix.build();
```

Shuffling reorders admission for the task's direct children. The effective seed and resolved order are recorded in the
result (`shuffleSeed()`, `executionOrder()`), and the same definition plus seed always yields the same order.

## Sharing state

Each item owns its own instance. Cross-item mutable state in a static field is shared and unsynchronized; if items run in
parallel, guard it or use `isolated`.
