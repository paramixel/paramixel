---
title: DependentRandomSequential
description: Shuffled fail-fast execution.
---

# DependentRandomSequential

## Factories

```java
DependentRandomSequential.of(String name, List<Action> children)
DependentRandomSequential.of(String name, Action... children)
DependentRandomSequential.of(String name, long seed, List<Action> children)
DependentRandomSequential.of(String name, long seed, Action... children)
```

## Semantics

- children are shuffled before execution
- execution stops on the first failure in that shuffled order
- remaining children are skipped
- a seed makes the shuffled order reproducible

## Example

```java
Action action = DependentRandomSequential.of(
        "suite",
        42L,
        Direct.of("a", context -> {}),
        Direct.of("b", context -> {}));
```