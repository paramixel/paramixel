---
title: Retry and CleanUp
description: Support utilities for transient failures and cleanup.
---

# Retry and CleanUp

The `org.paramixel.api.support` package contains support utilities that can be used inside steps.

## Retry

`Retry` runs a `ThrowingRunnable` under a policy and records the result.

```java
import org.paramixel.api.support.Retry;

Retry.of(policy)
        .retryOn(throwable -> !(throwable instanceof Error))
        .onRetry((attempt, throwable) -> logRetry(attempt, throwable))
        .runAndThrow(() -> callRemoteService());
```

`Retry` instances are not thread-safe; do not share one instance across parallel actions.

## CleanUp

`CleanUp` captures cleanup failures and can run multiple cleanup operations while preserving thrown failures.

```java
import org.paramixel.api.ThrowingRunnable;
import org.paramixel.api.support.CleanUp;

CleanUp.runAndThrow(
        CleanUp.of((ThrowingRunnable) () -> closeBrowser()),
        CleanUp.of((ThrowingRunnable) () -> stopServer()));
```

Use these utilities from action code; they are not discovery annotations or runner configuration mechanisms.
