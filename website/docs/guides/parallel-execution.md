---
title: Parallel Execution
description: Running tests concurrently with Parallel and Instance actions.
---

# Parallel Execution

Paramixel supports concurrent test execution through the `Parallel` action and configurable parallelism limits.

## Parallel action

`Parallel` runs child actions concurrently, bounded by a configurable parallelism limit:

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

## Parallelism limits

| Value | Behavior |
| --- | --- |
| `Integer.MAX_VALUE` (default) | Unlimited — all children run simultaneously |
| `1` | Sequential — one child at a time |
| `N` | At most `N` children execute concurrently |

Children are submitted in declaration order. A counter gates submission to respect the parallelism limit.

## Configuration

### Runner parallelism

`paramixel.parallelism` controls the default runner scheduler concurrency:

```properties
paramixel.parallelism=8
```

Default is `Runtime.getRuntime().availableProcessors()`.

### Per-action parallelism

`Parallel.of(name).parallelism(N)` sets a per-action limit independent of the runner-level setting:

```java
Parallel.of("limited")
        .parallelism(2)  // at most 2 children at a time
        .child(testA)
        .child(testB)
        .child(testC)
        .child(testD)
        .resolve();
```

## Nesting parallel actions

Parallel actions can be nested inside other composites:

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

## Data-driven parallel testing

Combine `Parallel` with the Spec + loop pattern for concurrent parameterized testing:

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

## Independent vs dependent

`Parallel` is always independent — all children are submitted regardless of sibling outcomes. This is because concurrent submission means earlier outcomes cannot gate later children that have already started.

For dependent sequential execution, use `Sequential` or `Lifecycle` instead.

## Thread safety

Framework-provided actions are immutable and reusable across concurrent runs. Custom actions with mutable instance state must handle their own synchronization. Prefer `ExecutionContext#instance(Class)` for per-execution mutable state.

## Large-scale parallel execution

When executing Parallel actions with many children (hundreds or thousands), Paramixel implements automatic backpressure to prevent scheduler thread exhaustion:

- The scheduler maintains a ready queue with a default capacity of 1024 tasks
- When the queue reaches 90% capacity, child scheduling pauses automatically
- Completion callbacks resume scheduling as queued tasks finish
- This prevents deadlock scenarios where all scheduler threads block waiting for queue space

For workloads with very large numbers of children:
- Use reasonable parallelism limits (e.g., 10-50) rather than unbounded parallelism
- Consider nesting Parallel actions to create hierarchical execution structures
- Monitor execution times and adjust parallelism based on the nature of your tests (I/O-bound vs CPU-bound)

## Next steps

- [Elements: Parallel](../core-concepts/elements#parallel)
- [Data-Driven Testing](../core-concepts/data-driven-testing)
- [Best Practices](./best-practices)
