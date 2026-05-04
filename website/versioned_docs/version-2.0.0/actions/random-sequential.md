---
title: RandomSequential
description: Execute all children in shuffled order.
---

# RandomSequential

## Factories

```java
RandomSequential.of(String name, List<Action> children)
RandomSequential.of(String name, Action... children)
RandomSequential.of(String name, long seed, List<Action> children)
RandomSequential.of(String name, long seed, Action... children)
```

## Semantics

- children are shuffled before execution
- all children still run
- a seed makes the order reproducible

## Example

```java
Action action = RandomSequential.of(
        "suite",
        42L,
        Direct.of("a", context -> {}),
        Direct.of("b", context -> {}),
        Direct.of("c", context -> {}));
```