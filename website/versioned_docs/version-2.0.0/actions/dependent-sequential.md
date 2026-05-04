---
title: DependentSequential
description: Fail-fast ordered execution.
---

# DependentSequential

## Factories

```java
DependentSequential.of(String name, List<Action> children)
DependentSequential.of(String name, Action... children)
```

## Semantics

`DependentSequential` executes children in order and stops on the first child failure.

When a child fails:

- remaining children are skipped
- the parent result becomes `FAIL`

A skipped child does not trigger fail-fast by itself.

## Example

```java
Action action = DependentSequential.of(
        "suite",
        Direct.of("first", context -> {}),
        Direct.of("second", context -> {
            FailException.fail("boom");
        }),
        Direct.of("third", context -> {}));
```