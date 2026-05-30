---
title: Retry and CleanUp
description: Support utilities for retrying transient failures and managing cleanup with throwable capture.
---

# Retry and CleanUp

The `org.paramixel.api.support` package provides two utilities for common testing patterns: **Retry** for operations that may fail transiently, and **CleanUp** for executing cleanup code while capturing throwables.

## Retry

`Retry` retries a `ThrowingRunnable` with configurable backoff and a wall-clock duration budget.

```java
import org.paramixel.api.support.Retry;
import org.paramixel.api.support.Retry.Policy;
```

### Creating a retry sequence

```java
Retry retry = Retry.of(Policy.exponential(
    Duration.ofMillis(100),
    Duration.ofSeconds(5)));
```

### Configuring retry behavior

**`retryOn(Predicate<Throwable>)`** — Controls which throwables are retryable. The default retries on any throwable that is not an `Error`. When the predicate returns `false`, the retry sequence stops immediately.

```java
retry.retryOn(throwable ->
    throwable instanceof java.net.ConnectException);
```

**`onRetry(BiConsumer<Integer, Throwable>)`** — Registers a callback invoked before each retry attempt. The callback receives the 1-based attempt number and the throwable from the previous failure. Multiple callbacks can be registered.

```java
retry.onRetry((attempt, cause) ->
    System.out.println("Retry attempt " + attempt + ": " + cause.getMessage()));
```

### Running the operation

**`run(ThrowingRunnable)`** — Runs the operation with retry behavior and returns a `Result`.

```java
Retry.Result result = retry.run(() -> {
    connectToService();
});

if (result.isSuccessful()) {
    System.out.println("Succeeded after " + result.attemptCount() + " attempt(s)");
} else {
    System.out.println("Failed after " + result.attemptCount() + " attempt(s)");
}
```

**`runAndThrow(ThrowingRunnable)`** — Same as `run()`, but rethrows the last captured exception when all retries are exhausted. Earlier exceptions are added as suppressed throwables.

```java
retry.runAndThrow(() -> {
    connectToService();
});
```

### Retry.Policy

The `Policy` interface defines the backoff strategy:

| Method | Description |
| --- | --- |
| `waitDuration(int attempt, Throwable cause)` | Returns the delay before the next retry attempt |
| `maximumDuration()` | Returns the total wall-clock duration budget |

**Built-in policies:**

| Factory Method | Description |
| --- | --- |
| `Policy.fixed(initialDelay, maximumDuration)` | Linear escalation: each retry waits `initialDelay * attempt` |
| `Policy.exponential(initialDelay, maximumDuration)` | Exponential escalation: each retry waits `initialDelay * 2^(attempt-1)` |

```java
Policy fixed = Policy.fixed(Duration.ofMillis(200), Duration.ofSeconds(10));
Policy exponential = Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(5));
```

Custom policies implement the `Policy` interface:

```java
Policy custom = new Policy() {
    @Override
    public Duration waitDuration(int attempt, Throwable cause) {
        return Duration.ofMillis(500);
    }

    @Override
    public Duration maximumDuration() {
        return Duration.ofSeconds(30);
    }
};
```

If a policy computation fails, implementations should throw `PolicyException`. The `Retry` loop catches `PolicyException` and treats the failed computation as zero, allowing the sequence to continue.

### Retry.Result

The `Result` describes the outcome of a retry run:

| Method | Description |
| --- | --- |
| `maximumDuration()` | The configured wall-clock duration budget |
| `attemptCount()` | The number of attempts executed |
| `elapsedDuration()` | The total wall-clock duration from first attempt to termination |
| `isSuccessful()` | Whether the operation succeeded within the budget |
| `hasExceptions()` | Whether any attempt produced an exception |
| `exception(int index)` | The exception for the attempt at the 0-based index, or empty if out of range |
| `exceptions()` | Every captured exception in attempt order |

### Lifecycle methods

| Method | Description |
| --- | --- |
| `hasRun()` | Whether `run()` or `runAndThrow()` has been called |
| `reset()` | Marks as not yet run without removing callbacks |
| `clear()` | Removes all callbacks and resets execution state |

:::caution
`Retry` is **not thread-safe**. Instances must not be shared across threads.
:::

## CleanUp

`CleanUp` is a single-use wrapper that executes a `ThrowingRunnable` and captures any throwable thrown during execution. Inspired by JUnit 5's `ThrowableCollector`.

```java
import org.paramixel.api.support.CleanUp;
```

### Creating a CleanUp

```java
CleanUp cleanup = CleanUp.of(() -> database.close());
CleanUp fromCloseable = CleanUp.of(inputStream);
CleanUp noOp = CleanUp.of((AutoCloseable) null);
```

`CleanUp.of(AutoCloseable)` accepts `null` — calling `run()` on a null closeable does nothing.

### Running cleanup

**`run()`** — Executes the runnable and captures any non-unrecoverable throwable. Unrecoverable errors (non-`StackOverflowError` `VirtualMachineError`) are rethrown immediately.

**`runAndThrow()`** — Same as `run()`, but throws the captured throwable if one was recorded.

```java
CleanUp cleanup = CleanUp.of(() -> connection.close());
// ... test logic ...
cleanup.runAndThrow();
```

### Inspecting the result

| Method | Description |
| --- | --- |
| `throwable()` | The captured throwable, or `null` if the runnable succeeded |
| `hasRun()` | Whether `run()` or `runAndThrow()` has been called |
| `isEmpty()` | Whether no throwable was captured |
| `isNotEmpty()` | Whether a throwable was captured |

### Running multiple CleanUp instances

The static `runAndThrow(CleanUp...)` and `runAndThrow(Collection<CleanUp>)` methods run all supplied instances and throw the first captured throwable with subsequent throwables added as suppressed exceptions:

```java
CleanUp db = CleanUp.of(() -> database.close());
CleanUp conn = CleanUp.of(() -> connection.close());

try {
    // ... test logic ...
} finally {
    CleanUp.runAndThrow(db, conn);
}
```

Each instance is run only once. If an instance has already been run, its previously captured throwable is collected without re-execution.

:::caution
Each `CleanUp` instance may only be run once. Subsequent calls to `run()` or `runAndThrow()` throw `IllegalStateException`.
:::

## Integration with actions

Use `Retry` and `CleanUp` inside `Step` or `Lifecycle` actions:

```java
@Paramixel.Factory
public static Action<?> factory() {
    return Instance.of(MyTest.class)
        .child(Lifecycle.of("lifecycle")
            .before("setUp()", MyTest::setUp)
            .child("test()", MyTest::test)
            .after("tearDown()", MyTest::tearDown)
            .resolve())
        .resolve();
}

// Inside a test method:
public void test() {
    Retry.of(Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
        .retryOn(e -> e instanceof java.net.ConnectException)
        .onRetry((attempt, cause) -> logger.warn("Retry " + attempt))
        .runAndThrow(() -> service.call());
}
```
