---
title: Action Composition
description: Combine built-in actions into test trees.
---

# Action Composition

Paramixel test plans are ordinary `Action` trees.

## Common composition patterns

### Ordered run-all

```java
Action suite = Sequential.of(
        "suite",
        Direct.of("step 1", context -> {}),
        Direct.of("step 2", context -> {}));
```

### Ordered fail-fast

```java
Action suite = StrictSequential.of(
        "suite",
        Direct.of("step 1", context -> {}),
        Direct.of("step 2", context -> {}));
```

### Concurrent children

```java
Action suite = Parallel.of(
        "suite",
        Direct.of("a", context -> {}),
        Direct.of("b", context -> {}));
```

### Setup / body / teardown

```java
Action suite = Lifecycle.of(
        "suite",
        Direct.of("before", context -> {}),
        Direct.of("main", context -> {}),
        Direct.of("after", context -> {}));
```

## Nesting

Actions can be nested arbitrarily.

```java
Action suite = Sequential.of(
        "suite",
        Lifecycle.of(
                "case 1",
                Direct.of("before", context -> {}),
                Parallel.of(
                        "checks",
                        Direct.of("a", context -> {}),
                        Direct.of("b", context -> {})),
                Direct.of("after", context -> {})),
        Noop.of("finished"));
```

## Choosing between sequential variants

- `Sequential` - always run every child
- `StrictSequential` - stop on first failure
- `RandomSequential` - shuffle, but still run every child
- `StrictRandomSequential` - shuffle and stop on first failure
