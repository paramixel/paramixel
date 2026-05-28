---
title: Parallel Execution
description: Running tests concurrently with Parallel and Instance actions.
---

# Parallel Execution

Paramixel supports concurrent test execution through the `Parallel` action and a three-layer parallelism model that controls concurrency at the runner, action, and scheduler levels.

## How `paramixel.parallelism` works

The `paramixel.parallelism` configuration key controls concurrency through three independent layers. Understanding how they interact is essential for tuning parallel execution.

```
┌─────────────────────────────────────────────────────────┐
│ Layer 1: Top-Level Parallel Throttle                    │
│   Semaphore(paramixel.parallelism)                      │
│   Gates how many root Parallel children run at once     │
├─────────────────────────────────────────────────────────┤
│ Layer 2: Per-Parallel Admission                          │
│   Each Parallel action limits its own in-flight children│
│   Defaults to paramixel.parallelism; capped by it       │
├─────────────────────────────────────────────────────────┤
│ Layer 3: Depth-Based Thread Pools + Leaf Permits        │
│   One pool per depth, created lazily by Parallel        │
│   Each pool sized to paramixel.parallelism              │
│   Global leaf semaphore limits concurrent leaf actions   │
└─────────────────────────────────────────────────────────┘
```

### Layer 1: Top-level Parallel throttle

When the root action is a `Parallel`, the runner creates a `TopLevelParallelThrottle` — a semaphore initialized with `paramixel.parallelism` permits. Each direct child of the root `Parallel` must acquire a permit before it begins executing. The permit is released when the child completes.

This prevents the root `Parallel` from flooding the scheduler with more in-flight branches than the configured parallelism allows. It is independent from the scheduler's leaf-permit accounting.

```properties
paramixel.parallelism=4
```

With the configuration above, at most 4 root-level `Parallel` children execute concurrently, even if the `Parallel` has 100 children.

### Layer 2: Per-Parallel admission

Every `Parallel` action gates its own direct children with an admission counter. When `.parallelism(N)` is not called, the effective parallelism defaults to `paramixel.parallelism`. When explicitly set, the effective parallelism is `min(configured, paramixel.parallelism)` — the per-action limit can never exceed the global setting.

```java
Parallel.of("limited")
        .parallelism(2)  // at most 2 children in-flight at this level
        .child(testA)
        .child(testB)
        .child(testC)
        .child(testD)
        .resolve();
```

Each `Parallel` admission slot covers one direct child regardless of whether that child is a `Step`, `Sequential`, or a nested `Parallel`. Nested `Parallel` actions enforce their own limits independently — a child `Parallel` with `.parallelism(8)` manages its own 8-slot admission counter inside the single parent slot it occupies.

### Layer 3: Depth-based thread pools and leaf permits

The scheduler creates one `ThreadPoolExecutor` per descriptor depth, lazily on first use. Each pool has a fixed size of `paramixel.parallelism` threads. A depth pool is only created when a `Parallel` action at depth N schedules its children at depth N+1 — non-Parallel composites (Sequential, Lifecycle, Instance, Static) execute their children synchronously on the calling thread and do not create depth pools.

This prevents same-depth starvation: a large `Parallel` at depth 0 cannot monopolize threads that depth-1 children need, because they run on a separate pool.

Additionally, a **global leaf semaphore** (also sized to `paramixel.parallelism`) limits how many leaf actions (`Step`, `Delay`, `AssertTrue`, `AssertFalse`) execute concurrently. Composite actions (`Parallel`, `Sequential`, `Lifecycle`, `Instance`, `Static`, `Repeat`, `Timeout`) do not consume leaf permits — only leaf actions do. This ensures that the total number of CPU-consuming work items is bounded regardless of tree structure.

```properties
paramixel.parallelism=8
paramixel.scheduler.queue.capacity=2048
```

`paramixel.scheduler.queue.capacity` bounds the ready queue for each depth executor (default 1024). When the queue is full, new submissions are rejected and the action fails.

## Parallel action

`Parallel` runs child actions concurrently, bounded by the three-layer model:

```java
Parallel.of("concurrent-suite")
        .parallelism(4)
        .child(Step.of("test-a", ctx -> {}))
        .child(Step.of("test-b", ctx -> {}))
        .child(Step.of("test-c", ctx -> {}))
        .child(Step.of("test-d", ctx -> {}))
        .resolve();
```

All children are always submitted regardless of individual outcomes. Status is computed after all children complete: `FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`.

## Per-action parallelism limits

| Value | Behavior |
| --- | --- |
| Not configured (default) | Inherits `paramixel.parallelism` at execution time |
| `1` | Sequential — one child at a time |
| `N` | At most `N` children in-flight, capped by `paramixel.parallelism` |

Children are submitted in declaration order. An admission counter gates submission to respect the parallelism limit. As each child completes, the next pending child is admitted.

## Nesting parallel actions

Parallel actions can be nested inside other composites. Each `Parallel` manages its own admission independently:

```java
Lifecycle.of("suite")
        .child(Parallel.of("parallel-group")
                .parallelism(2)
                .child(testA)
                .child(testB)
                .resolve())
        .child(Sequential.of("sequential-group")
                .child(testC)
                .child(testD)
                .resolve())
        .resolve();
```

When nesting `Parallel` inside `Parallel`:

```java
Parallel.of("root")
        .parallelism(4)
        .child(Parallel.of("inner-a")
                .parallelism(2)
                .child(step1)
                .child(step2)
                .child(step3)
                .resolve())
        .child(Parallel.of("inner-b")
                .parallelism(2)
                .child(step4)
                .child(step5)
                .child(step6)
                .resolve())
        .resolve();
```

In this example:
- **Layer 1**: Root `Parallel` has 2 children; both can be in-flight simultaneously (under the throttle of 4)
- **Layer 2**: `inner-a` admits at most 2 of its 3 children at a time; `inner-b` does the same independently
- **Layer 3**: Root-level children run on the depth-0 thread pool; `inner-a`/`inner-b` children run on the depth-1 pool; leaf `Step` actions consume leaf permits

## Data-driven parallel testing

Combine `Parallel` with the Spec + loop pattern for concurrent parameterized testing:

### For-loop

```java
@Paramixel.Factory
public static Action<?> factory() {
    var spec = Parallel.of("parameterized")
            .parallelism(4);
    for (TestCase tc : testCases()) {
        spec.child(argument(tc));
    }
    return spec.resolve();
}

private static Action<?> argument(TestCase tc) {
    return Lifecycle.of(tc.name())
            .before(Step.of("setup", ctx -> { /* setup */ }))
            .child(Step.of("test", ctx -> { /* test */ }))
            .after(Step.of("teardown", ctx -> { /* cleanup */ }))
            .resolve();
}
```

### Each convenience method

`Parallel.Spec.each(Iterable, Function)` produces an identical tree without the manual for-loop:

```java
@Paramixel.Factory
public static Action<?> factory() {
    return Parallel.of("parameterized")
            .parallelism(4)
            .each(testCases(), tc ->
                Lifecycle.of(tc.name())
                        .before(Step.of("setup", ctx -> { /* setup */ }))
                        .child(Step.of("test", ctx -> { /* test */ }))
                        .after(Step.of("teardown", ctx -> { /* cleanup */ })))
            .resolve();
}
```

The mapper is called at spec-building time, and an empty iterable produces an empty composite that passes cleanly.

## Independent vs dependent

`Parallel` is always independent — all children are submitted regardless of sibling outcomes. This is because concurrent submission means earlier outcomes cannot gate later children that have already started.

For dependent sequential execution, use `Sequential` or `Lifecycle` instead.

## Thread safety

Framework-provided actions are immutable and reusable across concurrent runs. Custom actions with mutable instance state must handle their own synchronization. Prefer `Context#instance(Class)` for per-execution mutable state.

## Large-scale parallel execution

When executing `Parallel` actions with many children (hundreds or thousands), the three-layer model provides natural backpressure:

- **Layer 1** prevents root-level branch explosion
- **Layer 2** prevents any single `Parallel` from flooding the scheduler with more in-flight children than its configured limit
- **Layer 3** bounds total leaf concurrency and isolates thread pools by depth, preventing starvation

The `paramixel.scheduler.queue.capacity` key bounds the ready queue per depth executor (default 1024). When a depth executor's queue is full, new submissions are rejected and the action fails with a `RejectedExecutionException`.

For workloads with very large numbers of children:
- Use reasonable per-action parallelism limits (e.g., 10-50) rather than unbounded parallelism
- Nest `Parallel` actions to create hierarchical execution structures — each level manages its own admission
- Monitor execution times and adjust `paramixel.parallelism` based on the nature of your tests (I/O-bound vs CPU-bound)
- Increase `paramixel.scheduler.queue.capacity` if you see `RejectedExecutionException` with many concurrent children

## Next steps

- [Elements: Parallel](../core-concepts/elements#parallel)
- [Data-Driven Testing](../core-concepts/data-driven-testing)
- [Configuration Properties](../configuration/properties)
- [Best Practices](./best-practices)
