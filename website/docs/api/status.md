---
title: Status
description: Outcome values in Paramixel.
---

# Status

`Status` is an immutable value: a `Kind` plus an optional message and throwable.

## Kinds

`Status.Kind` has six values, and `kind()` supports `switch`:

- `PENDING`
- `RUNNING`
- `PASSED`
- `FAILED`
- `SKIPPED`
- `ABORTED`

`Kind.isTerminal()` is `true` for the last four.

## Factories and constants

```java
Status.PASSED;                       // canonical, no detail
Status.failed("message");
Status.failed("message", throwable);
Status.skipped("message", throwable);
Status.aborted("message");
```

## Comparing

Compare with `kind()`, not with the canonical constants:

```java
if (result.status().kind() == Status.Kind.FAILED) { ... }
```

`Status.failed("x").equals(Status.FAILED)` is `false`, because the message differs. Equality compares `kind()` and
`message()` only; the throwable is ignored.

`isPending()`, `isRunning()`, `isPassed()`, `isFailed()`, `isSkipped()` and `isAborted()` are conveniences over
`kind()`.

## Aggregation

A container combines its children as `FAILED` &gt; `ABORTED` &gt; still-running &gt; `SKIPPED` &gt; `PASSED`. The run's
effective status is the root descriptor's status after promotion (`paramixel.failureOnSkip`,
`paramixel.failureOnAbort`, `paramixel.failIfNoTests`), which keeps the original message and throwable.
