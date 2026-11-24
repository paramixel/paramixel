---
title: Retry, Repeat, Timeout, and Cleanup
description: Bounded advanced policies with distinct semantics.
---

# Retry, Repeat, Timeout, and Cleanup

## Retry is not repeat

| Policy | Purpose | Stop condition |
| --- | --- | --- |
| `retry(RetryPolicy)` | Re-attempt a non-success outcome | Attempt limit, non-triggering status |
| `repeat(name, …)` | Intentional replay of a subtree | `maxIterations`, `untilPassed`, predicate |

A repeat `until` predicate is user code: it runs on an execution worker after each iteration, like a `when` condition.
If it throws, the failure is recorded on that iteration, the task fails, and repetition stops.

```java
step -> step
    .retry(RetryPolicy.builder()
            .maxAttempts(3)
            .backoff(Backoff.linear(Duration.ofMillis(100)))
            .build());
```

- Retry triggers default to `FAILED`, and only `FAILED` and `SKIPPED` are accepted (`RetryPolicy.RETRYABLE_KINDS`).
  `PASSED` is rejected so a passing task is never re-run, and `CANCELED` and `ABORTED` are rejected so cancellation and
  assumption failures are not retried automatically.
- Attempts never overlap.
- Retrying an owned `instance` task creates a fresh instance per attempt and runs its `after` callbacks between
  attempts; retrying a nested step reuses the inherited instance.
- A successful later attempt passes the task while all attempts remain visible in the result.

## Timeout

```java
task("bounded").timeout(Duration.ofSeconds(2)).step("work", Test::work);
```

The timeout uses a monotonic clock. On expiry the running callback is interrupted, no new work is admitted in the
subtree, and finalizers run only after quiescence. If a callback ignores interruption past
`paramixel.shutdown.graceMillis`, the result is `quiescent=false`: cleanup is withheld, permits stay held, and the exit
code is non-zero. Paramixel does not claim it can stop arbitrary Java code.

## Cleanup

`after` callbacks are the cleanup mechanism:

- They run in reverse declaration order and are all attempted.
- They are not implicit destructors: a fixture implementing `AutoCloseable` is not closed unless a callback closes it.
- A failure in one `after` callback does not prevent the others, and the original operational failure stays primary.
- Keyed resources are released after `after`, in reverse acquisition order. If cancellation interrupts acquisition,
  the runner stops starting new factories and releases resources acquired before acquisition stopped.
