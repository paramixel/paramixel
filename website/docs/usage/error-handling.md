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

Unexpected exceptions also trigger `Listener#actionThrowable(...)`.

## Composite actions

- `Sequential` runs all children and computes status afterward
- `StrictSequential` stops at first failure and skips remaining children
- `Parallel` waits for all children and computes status from them
- `Lifecycle` may skip `main` if `before` fails or skips, but still runs `after`

## Pre-execution validation

`Runner.run()` validates the action tree for structural problems before execution begins. If a problem is detected, an `IllegalStateException` is thrown and no actions execute.

### Thread-starvation deadlock

When deeply nested `Parallel` actions share the default executor with insufficient threads, `Runner` throws:

```
IllegalStateException: Potential thread-starvation deadlock detected: ...
```

The message includes:

- The detected nesting depth
- The configured parallelism
- Instructions for resolving the deadlock

See [Parallel: Deadlock Prevention](../actions/parallel.md#deadlock-prevention) for details.

## Cleanup failures

If you need to accumulate cleanup failures, use `Cleanup.runAndThrow()`, which throws the first failure and attaches the rest as suppressed exceptions.
