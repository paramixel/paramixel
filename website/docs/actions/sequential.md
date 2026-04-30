---
title: Sequential
description: Execute all children in order.
---

# Sequential

## Factories

```java
Sequential.of(String name, List<Action> children)
Sequential.of(String name, Action... children)
```

## Semantics

`Sequential` runs every child in order.

It does **not** stop on failure. Later children still execute even if an earlier child fails or skips.

Parent status is computed from child results:

- `FAIL` if any child failed
- otherwise `SKIP` if any child skipped
- otherwise `PASS`

## Example

```java
Action action = Sequential.of(
        "suite",
        Direct.of("first", context -> {}),
        Direct.of("second", context -> {}));
```
