---
title: Parallel
description: Execute child actions concurrently.
---

# Parallel

`Parallel` executes child actions concurrently. In 3.x it is builder-only.

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action checkA = checkA();
        Action checkB = checkB();

        return Parallel.builder("checks")
                .parallelism(4)
                .child(checkA)
                .child(checkB)
                .build();
    }

    private static Action checkA() {
        return Direct.builder("checkA").execute(context -> {}).build();
    }

    private static Action checkB() {
        return Direct.builder("checkB").execute(context -> {}).build();
    }
}
```

Optional settings:

- `.contextMode(Action.ContextMode mode)` controls the parallel action's own context scope.
- `.parallelism(int)` limits concurrent children.
- `.executorService(ExecutorService)` uses a caller-owned executor.

`Parallel` passes its effective context directly to children; each child applies its own `Action.ContextMode`.

When shared context is used with parallel children, store map operations are thread-safe, but mutable values stored in the store must also be thread-safe.

## Builder

```java
Parallel.builder(String name)
        .parallelism(int parallelism) // optional
        .executorService(ExecutorService executorService) // optional
        .child(Action child)
        .build()
```

Builders require a name up front, validate method arguments immediately, and are one-shot. The builder can combine `parallelism(...)` and `executorService(...)` for a node-local limit on a dedicated executor.

## Semantics

- children are submitted concurrently
- the action waits for all children to finish
- parent status is computed from child results
- when `parallelism` is provided, a semaphore limits concurrent children inside this node

`Parallel` uses an `ExecutorService`.

If no executor is supplied directly to the action, it uses `context.getExecutorService()`.

## Custom executor ownership

If you pass an `ExecutorService` to `Parallel.builder(...).executorService(...)`, Paramixel uses it but does not manage its lifecycle for you.

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

`CycleDetector` walks the action graph and rejects parent-child cycles. If a cycle is detected, it throws `CycleDetectedException`:

```
CycleDetectedException: Cycle detected in action graph: actionA[id1] -> actionB[id2] -> actionA[id1]
```

### Deadlock detection

`DeadlockDetector` detects nested `Parallel` configurations that would cause thread starvation.

```java
// This throws DeadlockDetected when paramixel.parallelism=1:
public class DangerTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action leaf1 = leaf1();
        Action leaf2 = leaf2();

        Action parallelC = Parallel.builder("C")
                .child(leaf1)
                .child(leaf2)
                .build();

        Action parallelB = Parallel.builder("B")
                .child(parallelC)
                .build();

        return Parallel.builder("A")
                .child(parallelB)
                .build();
    }

    private static Action leaf1() {
        return Direct.builder("leaf1").execute(ctx -> {}).build();
    }

    private static Action leaf2() {
        return Direct.builder("leaf2").execute(ctx -> {}).build();
    }
}

Runner.builder()
    .configuration(Map.of("paramixel.parallelism", "1"))
    .build()
    .run(DangerTest.actionFactory()); // throws DeadlockDetected
```

### The fix

There are two ways to resolve this:

1. **Supply dedicated executors** to inner `Parallel` actions (preferred for fine-grained control):

```java
public class SafeTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action leaf1 = leaf1();
        Action leaf2 = leaf2();

        Action parallelC = Parallel.builder("C")
                .child(leaf1)
                .child(leaf2)
                .build();

        Action parallelB = Parallel.builder("B")
                .executorService(innerEs)
                .child(parallelC)
                .build();

        return Parallel.builder("A")
                .child(parallelB)
                .build();
    }

    private static Action leaf1() {
        return Direct.builder("leaf1").execute(ctx -> {}).build();
    }

    private static Action leaf2() {
        return Direct.builder("leaf2").execute(ctx -> {}).build();
    }
}
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
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action a = a();
        Action b = b();

        return Parallel.builder("tests")
                .child(aAction)
                .child(bAction)
                .build();
    }

    private static Action a() {
        return Direct.builder("a").execute(context -> {}).build();
    }

    private static Action b() {
        return Direct.builder("b").execute(context -> {}).build();
    }
}
```

### Node-local concurrency limit

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action a = a();
        Action b = b();
        Action c = c();

        return Parallel.builder("tests")
                .parallelism(2)
                .child(aAction)
                .child(bAction)
                .child(cAction)
                .build();
    }

    private static Action a() {
        return Direct.builder("a").execute(context -> {}).build();
    }

    private static Action b() {
        return Direct.builder("b").execute(context -> {}).build();
    }

    private static Action c() {
        return Direct.builder("c").execute(context -> {}).build();
    }
}
```

### Custom executor

```java
public class MyTest {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action a = a();
        Action b = b();

        return Parallel.builder("tests")
                .executorService(executorService)
                .child(aAction)
                .child(bAction)
                .build();
    }

    private static Action a() {
        return Direct.builder("a").execute(context -> {}).build();
    }

    private static Action b() {
        return Direct.builder("b").execute(context -> {}).build();
    }
}
```

### Builder with custom executor and limit

```java
public class MyTest {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action a = a();
        Action b = b();

        return Parallel.builder("tests")
                .executorService(executorService)
                .parallelism(2)
                .child(aAction)
                .child(bAction)
                .build();
    }

    private static Action a() {
        return Direct.builder("a").execute(context -> {}).build();
    }

    private static Action b() {
        return Direct.builder("b").execute(context -> {}).build();
    }
}
```
