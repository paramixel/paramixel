---
title: Data-Driven Testing
description: Typed matrices with each, without casts or repeated class tokens.
---

# Data-Driven Testing

A matrix is a loop over the items while the definition is authored. Each item produces its own builder, bindings, and
hooks, so every item can hold a different fixture type with no casts and no repeated class token.

```java
final var root = Parallel.builder(MySuite.class, "Environments", Pool.named("environments"));
for (final var environment : environments) {
    root.step(Sequential.builder(environment.name())
            .bind(ENVIRONMENT, environment)
            .before(ID_START)
            .step(ID_VERIFY)
            .after(ID_STOP));
}
Parallel definition = root.build();
```

## Rules

- Iteration order is preserved; a set or map with nondeterministic iteration order is the caller's choice and is not
  presented as stable.
- The loop runs while the definition is authored. It may capture application objects, but must not run test bodies,
  create fixtures, or acquire runtime resources: those belong in the branch's `before` slot.
- A builder passed to `step` is held until the parent is built, so a branch helper can be configured further before
  `build()`.
- Heterogeneous branches are allowed: one item may bind `A` and another bind `B`, with no casts.

## Shuffling

```java
final var matrix = Parallel.builder(MySuite.class, "Matrix", Pool.named("environments")).shuffle(42L);
for (final var environment : environments) {
    matrix.step(Sequential.builder(environment.name()).bind(ENVIRONMENT, environment).step(ID_VERIFY));
}
Parallel definition = matrix.build();
```

Shuffling reorders admission for the task's direct children. The effective seed and resolved order are recorded in the
result (`shuffleSeed()`, `executionOrder()`), and the same definition plus seed always yields the same order.

## Sharing state

Each branch owns its own context scope, and a value bound on a branch is visible only inside that branch. Cross-branch
mutable state in a static field is shared and unsynchronized; if branches run in parallel, guard it or use `isolated`.
