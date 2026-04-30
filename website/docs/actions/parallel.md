---
title: Parallel
description: Execute child actions concurrently.
---

# Parallel

## Factories

```java
Parallel.of(String name, List<Action> children)
Parallel.of(String name, int parallelism, List<Action> children)
Parallel.of(String name, Action... children)
Parallel.of(String name, int parallelism, Action... children)
Parallel.of(String name, ExecutorService executorService, List<Action> children)
Parallel.of(String name, ExecutorService executorService, Action... children)
```

## Semantics

- children are submitted concurrently
- the action waits for all children to finish
- parent status is computed from child results
- when `parallelism` is provided, a semaphore limits concurrent children inside this node

`Parallel` uses an `ExecutorService`.

If no executor is supplied directly to the action, it uses `context.getExecutorService()`.

## Custom executor ownership

If you pass an `ExecutorService` to `Parallel.of(...)`, Paramixel uses it but does not manage its lifecycle for you.

## Examples

### Default executor

```java
Action action = Parallel.of(
        "tests",
        Direct.of("a", context -> {}),
        Direct.of("b", context -> {}));
```

### Node-local concurrency limit

```java
Action action = Parallel.of(
        "tests",
        2,
        Direct.of("a", context -> {}),
        Direct.of("b", context -> {}),
        Direct.of("c", context -> {}));
```

### Custom executor

```java
ExecutorService executorService = Executors.newFixedThreadPool(4);
Action action = Parallel.of("tests", executorService, children);
```

See also the examples under:

- `examples/test/argument/ArgumentParallelismTest.java`
- `examples/test/argument/ArgumentNestedParallelismTest.java`
- `examples/test/argument/ArgumentCustomExecutorServiceTest.java`
