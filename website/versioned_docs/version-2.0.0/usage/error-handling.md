---
title: Error Handling
description: Failures, skips, and thrown exceptions.
---

# Error Handling

## Leaf actions

In `Direct`:

- return normally -> `PASS`
- throw `FailException` -> `FAIL`
- throw `SkipException` -> `SKIP`
- throw any other exception -> `FAIL`
- throw `Error` -> propagates immediately (not caught)

Unexpected exceptions also trigger `Listener#actionThrowable(...)`.

`Error` subclasses (such as `OutOfMemoryError`, `StackOverflowError`, and `ThreadDeath`) are never caught by the framework. They propagate immediately and terminate execution.

## Composite actions

- `Sequential` runs all children and computes status afterward
- `DependentSequential` stops at first failure and skips remaining children
- `Parallel` waits for all children and computes status from them. If the executing thread is interrupted during semaphore acquisition, `Parallel` sets a `FAIL` result, fires `afterAction`, and re-throws a `RuntimeException` wrapping the `InterruptedException`
- `Lifecycle` may skip `main` if `before` fails or skips, but still runs `after`

## Skipped action context

When actions are skipped, each one receives its own child context that mirrors the action tree — the same context hierarchy as normal execution. Listener callbacks interleave the same way: parent `beforeAction`, then children, then parent `afterAction`. This means `getParent()`, `findAncestor()`, and `getStore()` work identically whether an action executed or was skipped.

The `skipAction(Result)` listener callback fires for every skipped action.

## Pre-execution validation

`Runner.run()` validates the action tree for structural problems before execution begins. If a problem is detected, an exception is thrown and no actions execute.

### Cycle detection

`CycleLoopDetector` detects parent-child cycles in the action graph. If a cycle is found, it throws `CycleDetectedException`:

```
CycleDetectedException: Cycle detected in action graph: actionA[id1] -> actionB[id2] -> actionA[id1]
```

### Thread-starvation deadlock

`DeadlockDetector` detects nested `Parallel` configurations that would cause thread starvation. When detected, it throws `DeadlockDetected`:

```
DeadlockDetected: Potential thread-starvation deadlock detected: ...
```

The message includes:

- The detected nesting depth
- The configured parallelism
- Instructions for resolving the deadlock

See [Parallel: Pre-execution validation](../actions/parallel.md#pre-execution-validation) for details.

## Cleanup failures

If you need to accumulate cleanup failures, use `Cleanup.runAndThrow()`, which throws the first failure and attaches the rest as suppressed exceptions.

`Error` subclasses thrown by cleanup tasks are **not caught** and abort the cleanup loop immediately. Remaining cleanup tasks will not run.