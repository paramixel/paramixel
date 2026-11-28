---
title: Retry, Repeat, Timeout, and Cleanup
description: Bounded advanced policies with distinct semantics.
---

# Retry, Repeat, Timeout, and Cleanup

## Retry is not repeat

| Policy | Purpose | Stop condition |
| --- | --- | --- |
| `retry(RetryPolicy)` | Re-attempt a non-success outcome | Attempt limit, non-triggering status |
| `Repeat.builder(name)` | Intentional replay of a subtree | `maxIterations`, `untilPassed`, predicate |

Both policies require a finite bound between 1 and 10,000: `RetryPolicy.MAX_ATTEMPTS` and
`RepeatPolicy.MAX_ITERATIONS`. A larger value is rejected as unbounded work.

A repeat `until` predicate is user code: it runs on an execution worker after each iteration, like an `onlyIf`
condition. If it throws, the failure is recorded on that iteration, the task fails, and repetition stops.

```java
Sequential.builder(MySuite.class, "flaky")
        .retry(RetryPolicy.builder()
                .maxAttempts(3)
                .backoff(Backoff.linear(Duration.ofMillis(100)))
                .build())
        .step("probe");
```

- Retry triggers default to `FAILED`, and only `FAILED` and `SKIPPED` are accepted (`RetryPolicy.RETRYABLE_KINDS`).
  `PASSED` is rejected so a passing task is never re-run, and `CANCELED` and `ABORTED` are rejected so cancellation and
  assumption failures are not retried automatically.
- Attempts never overlap.
- A retried task owns its context scope, so an owned fixture is rebuilt per attempt: the `before` hook runs again and
  the `after` slot runs between attempts.
- A successful later attempt passes the task while all attempts remain visible in the result.

## Timeout

```java
Sequential.builder(MySuite.class, "bounded").timeout(Duration.ofSeconds(2)).step("work");
```

The timeout uses a monotonic clock. On expiry the running callback is interrupted, no new work is admitted in the
subtree, and finalizers run only after quiescence. If a callback ignores interruption past
`paramixel.shutdown.graceMillis`, the result is `quiescent=false`: cleanup is withheld, permits stay held, and the exit
code is non-zero. Paramixel does not claim it can stop arbitrary Java code.

## Cleanup

The `after` slot is the cleanup mechanism:

- It runs once the node has started, whatever failed before it, and it is a single slot.
- Several cleanup phases are composed into one subtree:
  `.after(Sequential.builder("cleanup").independent().step(tearDownB).step(tearDownA))`. A `Sequential` cleanup subtree
  is `dependent()` by default, so a failing cleanup step skips the later ones; `independent()` makes every step run.
- They are not implicit destructors: a fixture implementing `AutoCloseable` is not closed unless a slot closes it.
- The original operational failure stays primary; a cleanup failure adds to the occurrence's failures without hiding it.
- If cancellation interrupts the run, an `after` subtree still runs: finalization is not skipped. A cleanup that hangs is
  still interrupted, and a cleanup that ignores interruption produces a non-quiescent result.
