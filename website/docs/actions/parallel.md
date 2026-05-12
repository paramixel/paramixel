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
- `.parallelism(int)` limits concurrent direct children for this `Parallel` node.
- `.scheduler(AsyncScheduler)` executes this `Parallel` subtree with a custom scheduler.

`Parallel` passes its effective context directly to children; each child applies its own `Action.ContextMode`.

When shared context is used with parallel children, store map operations are thread-safe, but mutable values stored in the store must also be thread-safe.

## Builder

```java
Parallel.builder(String name)
        .contextMode(Action.ContextMode mode) // optional
        .parallelism(int parallelism) // optional
        .scheduler(AsyncScheduler scheduler) // optional
        .child(Action child)
        .build()
```

Builders require a name up front, validate method arguments immediately, and are one-shot.

## Semantics

- children are scheduled concurrently
- the action waits for all admitted children to finish
- parent status is computed from child results
- `Parallel.parallelism` limits concurrent direct children inside this node
- `paramixel.parallelism` limits global worker concurrency for the default scheduler

Nested `Parallel` trees do not require deadlock workarounds. The default scheduler owns worker execution and can continue other ready work while a `Parallel` has queued direct children.

## Custom Scheduler

`Parallel.builder(...).scheduler(...)` is an advanced extension point. It replaces the scheduler for that `Parallel` subtree only.

The custom scheduler receives each direct child admitted by the `Parallel` node. Any descendant call to `Context.runAsync(...)` also uses the custom scheduler while executing inside that subtree.

Custom schedulers must return a `CompletableFuture<Result>` and must complete that future with the scheduled action's result or complete it exceptionally.

```java
public final class MyScheduler implements AsyncScheduler {

    @Override
    public CompletableFuture<Result> runAsync(Action action, Context context) {
        try {
            return CompletableFuture.completedFuture(action.execute(context));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }
}
```

Use it from a `Parallel` builder:

```java
private static final AsyncScheduler scheduler = new MyScheduler();

@Paramixel.ActionFactory
public static Action actionFactory() {
    return Parallel.builder("tests")
            .scheduler(scheduler)
            .parallelism(2)
            .child(a())
            .child(b())
            .build();
}
```

## Pre-execution Validation

`DefaultRunner` validates the action tree for structural problems before execution begins.

### Cycle detection

`CycleDetector` walks the action graph and rejects parent-child cycles. If a cycle is detected, it throws `CycleDetectedException`:

```
CycleDetectedException: Cycle detected in action graph: actionA[id1] -> actionB[id2] -> actionA[id1]
```

## Examples

### Default scheduler

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Parallel.builder("tests")
                .child(a())
                .child(b())
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
        return Parallel.builder("tests")
                .parallelism(2)
                .child(a())
                .child(b())
                .child(c())
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
