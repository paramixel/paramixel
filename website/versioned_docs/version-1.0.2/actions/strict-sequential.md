---
title: StrictSequential
description: Fail-fast ordered execution.
---

# StrictSequential

## Factories

```java
StrictSequential.of(String name, List<Action> children)
StrictSequential.of(String name, Action... children)
```

## Semantics

`StrictSequential` executes children in order and stops on the first child failure.

When a child fails:

- remaining children are skipped
- the parent result becomes `FAIL`

A skipped child does not trigger fail-fast by itself.

## Example

```java
Action action = StrictSequential.of(
        "suite",
        Direct.of("first", context -> {}),
        Direct.of("second", context -> {
            FailException.fail("boom");
        }),
        Direct.of("third", context -> {}));
```
