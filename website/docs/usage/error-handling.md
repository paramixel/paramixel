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

## Cleanup failures

If you need to accumulate cleanup failures, use `Cleanup.runAndThrow()`, which throws the first failure and attaches the rest as suppressed exceptions.
