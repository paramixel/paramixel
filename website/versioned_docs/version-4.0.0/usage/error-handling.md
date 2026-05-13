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
- throw `OutOfMemoryError` or `StackOverflowError` -> propagates immediately
- throw other `Error` subclasses -> `FAIL`

Unexpected exceptions also trigger `Listener#actionThrowable(...)`.

`OutOfMemoryError` and `StackOverflowError` are treated as unrecoverable and are never caught by the framework. Other `Error` subclasses, such as `ThreadDeath`, are captured like other unexpected throwables and reported as failures.

## Composite actions

- `Container` with `ChildMode.INDEPENDENT` runs all body children and computes status afterward
- `Container` with `ChildMode.DEPENDENT` stops body execution at the first failure or skip and skips remaining body children
- `Parallel` waits for all children and computes status from them
- `Container` skips body children if `before(...)` fails or skips, but still runs `after(...)`

## Skipped action context

When actions are skipped by framework control flow, built-in actions apply the same context scoping as normal running: `Container` shares its context with `before`/`after` and creates child contexts for body children, and `Parallel` creates a child context for each child. Listener callbacks interleave the same way: parent `beforeAction`, then children, then parent `afterAction`. This means `getParent()`, `findParent()`, `getAncestor()`, `findAncestor()`, and `getStore()` follow the same context-mode rules whether an action executed or was skipped.

The `skipAction(Result)` listener callback fires for actions skipped through `Action.skip(...)`, such as descendants skipped after a dependent parent stops. A `SkipException` thrown from a running `Direct` action produces a `SKIP` result and flows through `afterAction(Result)`.

## Pre-run validation

`Runner.run()` validates the action tree for structural problems before the run begins. If a problem is detected, an exception is thrown and no actions run.

### Cycle detection

`CycleDetector` detects parent-child cycles in the action graph. If a cycle is found, it throws `CycleDetectedException`:

```
CycleDetectedException: Cycle detected in action graph: actionA[id1] -> actionB[id2] -> actionA[id1]
```

See [Parallel: Pre-run validation](../actions/parallel.md#pre-run-validation) for details.

## Cleanup failures

If you need to accumulate cleanup failures, use `Cleanup.runAndThrow()`, which throws the first failure and attaches the rest as suppressed exceptions.

`OutOfMemoryError` and `StackOverflowError` thrown by cleanup tasks are **not caught** and abort the cleanup loop immediately. Other cleanup failures are accumulated by `Cleanup`.
