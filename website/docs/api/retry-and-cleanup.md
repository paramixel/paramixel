---
title: Retry and CleanUp
description: Support utilities for transient failures and cleanup.
---

# Retry and CleanUp

The `org.paramixel.api.support` package contains support utilities that can be used inside steps.

## Retry

`Retry` runs a `ThrowingRunnable` under a `Backoff` schedule and a wall-clock budget, and returns a `Result`.

```java
import java.time.Duration;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.Retry;

var retry = Retry.of(Backoff.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)), Duration.ofSeconds(10))
        .retryOn(throwable -> !(throwable instanceof Error))
        .onRetry((attempt, throwable) -> logRetry(attempt, throwable));

retry.runAndThrow(() -> callRemoteService());
```

`Retry` is an immutable configuration: `retryOn` and `onRetry` return a new instance, and `run` may be called any
number of times, so one `Retry` can be reused. `Backoff` is the same type `Loop.Builder.delay` takes:

```java
Backoff.fixed(Duration.ofMillis(100));                       // the same wait every time
Backoff.linear(Duration.ofMillis(100));                      // 100ms, 200ms, 300ms, …
Backoff.exponential(Duration.ofMillis(50), Duration.ofSeconds(5)); // doubles, saturating at 5s
```

`Backoff` arithmetic saturates: a delay never exceeds its maximum and never throws, so a long loop or a high retry
count is safe.

## CleanUp

`CleanUp` captures cleanup failures and can run multiple cleanup operations while preserving thrown failures.

```java
import org.paramixel.api.ThrowingRunnable;
import org.paramixel.api.support.CleanUp;

CleanUp.runAndThrow(
        CleanUp.of((ThrowingRunnable) () -> closeBrowser()),
        CleanUp.of((ThrowingRunnable) () -> stopServer()));
```

After a run, `cleanUp.throwable()` returns an `Optional<Throwable>`.

Use these utilities from action code; they are not discovery annotations or runner configuration mechanisms.
