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

## Runner executor ownership

When no `ExecutorService` is supplied to `Runner.builder()`, each `run()` call creates two thread pools:

1. **Runner pool** — used for top-level `Parallel` tasks (threads named `paramixel-runner-N`)
2. **Parallel pool** — used for nested `Parallel` tasks using the default executor (threads named `paramixel-parallel-N`)

Both pools are shut down when `run()` completes. A `Runner` can be called multiple times; each call gets fresh pools.

When `Runner.builder().executorService(myPool)` is used:

- `myPool` is used for top-level action dispatch
- The parallel pool is still created and managed per `run()` call
- `myPool` is **never** shut down by the runner — you control its lifecycle

## Pre-execution validation

`DefaultRunner` validates the action tree for structural problems before execution begins.

### Cycle detection

`CycleLoopDetector` walks the action graph and rejects parent-child cycles. If a cycle is detected, it throws `CycleDetectedException`:

```
CycleDetectedException: Cycle detected in action graph: actionA[id1] -> actionB[id2] -> actionA[id1]
```

### Deadlock detection

`DeadlockDetector` detects nested `Parallel` configurations that would cause thread starvation.

```java
// This throws DeadlockDetected when paramixel.parallelism=1:
Action danger = Parallel.of("A",
        Parallel.of("B",
                Parallel.of("C",
                        Direct.of("leaf1", ctx -> {}),
                        Direct.of("leaf2", ctx -> {}))));

Runner.builder()
    .configuration(Map.of("paramixel.parallelism", "1"))
    .build()
    .run(danger); // throws DeadlockDetected
```

### The fix

There are two ways to resolve this:

1. **Supply dedicated executors** to inner `Parallel` actions (preferred for fine-grained control):

```java
ExecutorService innerEs = Executors.newFixedThreadPool(4);
Action safe = Parallel.of("A",
        Parallel.of("B", innerEs,
                Parallel.of("C",
                        Direct.of("leaf1", ctx -> {}),
                        Direct.of("leaf2", ctx -> {}))));
```

2. **Increase `paramixel.parallelism`** to match the nesting depth:

```java
Runner.builder()
    .configuration(Map.of("paramixel.parallelism", "2"))
    .build()
    .run(danger); // safe with 2 threads
```

### When detection resets

Detection resets the depth counter at any `Parallel` node that has a custom `ExecutorService`, because custom executors provide their own thread pool and do not contend for shared pool threads.

## RoutingExecutorService

Internally, `DefaultRunner` uses a `RoutingExecutorService` that routes submitted tasks between two delegate executor services based on execution depth:

- Depth 0 (root level) → runner executor service
- Depth > 0 (nested parallel) → parallel executor service

This prevents thread starvation in typical nested `Parallel` configurations without requiring users to supply custom executors.

## Interrupt handling

If the executing thread is interrupted during concurrency semaphore acquisition, the `Parallel` action transitions to a `FAIL` result with the `InterruptedException` as the cause, fires `afterAction`, and then re-interrupts the thread before re-throwing a `RuntimeException` wrapping the cause. This ensures the lifecycle contract (result transitions to a terminal state and `afterAction` is always invoked) is honored even under interrupt conditions.

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