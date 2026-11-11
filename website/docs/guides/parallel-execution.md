---
title: Parallel Execution
description: Named pools, permit lifetimes, admission order, and honest limits.
---

# Parallel Execution

## Pools are budgets

```java
private static final Pool ENVIRONMENTS = Pool.named("environments");

Task definition = parallel("Compatibility matrix", ENVIRONMENTS)
        .each(environments, env -> forInstance(env.name(), () -> new Test(env))
                .step("check", Test::check))
        .build();

Runner runner = Runner.builder().pool(ENVIRONMENTS, 3).build();
```

A `Pool` is a value object: no threads, no global registration, no permits until a run compiles a plan. Capacity
precedence is `Runner.Builder.pool` → `paramixel.parallelism.<name>` → error for a user pool.
`paramixel.workers` bounds execution threads separately, so a pool capacity does not silently become a thread count.

## Permit lifetime

Each direct child of a parallel task holds exactly one permit from admission through instance creation, `before`, body,
nested groups, `after`, and resource release. A parent does not consume its own children's permits. Nested groups with a
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
- Provable pool/lock acquisition-order cycles are rejected with both resource names and the offending task path.
- A scheduler that cannot make progress fails the run with a diagnostic instead of hanging.

## Honest limits

Paramixel cannot preempt a running callback, make a shared fixture thread-safe, or forcibly stop code that ignores
interruption. Parallel children sharing an inherited instance are concurrent calls on that object; guard, isolate, or
split the scope.
