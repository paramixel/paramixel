---
title: Parallel Execution
description: Named pools, permit lifetimes, admission order, and honest limits.
---

# Parallel Execution

## Pools are budgets

```java
private static final Pool ENVIRONMENTS = Pool.named("environments");

final var root = Parallel.builder(MySuite.class, "Compatibility matrix", ENVIRONMENTS);
for (final var environment : environments) {
    root.step(Sequential.builder(environment.name()).bind(ENVIRONMENT, environment).step("check"));
}
Task definition = root.build();

Runner runner = Runner.builder().pool(ENVIRONMENTS, 3).build();
```

A `Pool` is a value object: no threads, no global registration, no permits until a run compiles a plan. Capacity
precedence is `Runner.Builder.pool` → `paramixel.parallelism.<name>` → error for a user pool.
`paramixel.workers` bounds execution threads separately, so a pool capacity does not silently become a thread count.

## Capacity is a cap, not a reservation

A capacity bounds how many direct children of parallel tasks may hold a permit from the same pool at one time. It
reserves no share of the runner, creates no threads, and does not guarantee concurrency. Two limits apply together:

```
concurrent children of one pool ≤ min(pool capacity, free workers)
```

`paramixel.workers` (default: available processors) bounds how many task callbacks run at once across every pool, and
each run creates its own worker set. A pool configured with capacity 32 on a run limited to 8 workers runs at most 8 of
its children concurrently, and a pool whose permits are free still waits while every worker is busy elsewhere. Raising
a capacity above the worker limit cannot add concurrency; raise `paramixel.workers` for that.

Two consequences worth planning around:

- **No isolation or fairness between pools.** Ready work is dispatched to the shared worker set in compiled order, not
  by pool, so one pool's admitted children can occupy every worker and delay another pool's ready children. A pool
  protects the external resource it names, not its own share of the machine.
- **Size the two numbers for different questions.** A pool capacity answers "how much of this scarce thing may be in
  flight" (containers, ports, licenses, connection slots). `paramixel.workers` answers "how much work may this machine
  run at once".

## Permit lifetime

Each direct child of a parallel task holds exactly one permit from admission through its `before` slot, body, nested
groups, and `after` slot. A parent does not consume its own children's permits. Nested groups with a
different pool acquire additional permits for their own children.

## Admission order

Ready work is dispatched in compiled depth-first order, so descendants of an admitted branch are considered before an
unopened sibling. This is an admission preference, not preemption: work that already started runs to completion unless it
is canceled.

## Waiting is never a worker

Waiting for a permit, a lock, a backoff delay, or a child group is asynchronous. A parent never occupies a worker while
waiting for its children, so a one-worker run of a nested tree still completes.

## Deadlock protection

- Nesting the same pool on one ancestry path is rejected at compile time, regardless of configured capacity.
- Provable pool/lock acquisition-order cycles are rejected with both resource names and the offending task path. The
  check follows runtime order: a task takes its parent's pool permit, then its own `isolated` lock at admission, and
  only then do its children take permits of its own pool. So `Parallel.builder(..., pool).isolated(lock)` on one path and a
  `pool` child that is `isolated(lock)` on another path form a cycle.
- A scheduler that cannot make progress fails the run with a diagnostic instead of hanging.

## Honest limits

Paramixel cannot preempt a running callback, make a shared fixture thread-safe, or forcibly stop code that ignores
interruption. Parallel children reading a value bound on the parent are concurrent calls on that object; guard, isolate,
or split the scope.
