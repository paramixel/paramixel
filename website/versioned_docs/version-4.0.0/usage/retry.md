---
title: Retry
description: Retry operations with configurable backoff and a wall-clock duration budget.
---

# Retry

`org.paramixel.core.support.Retry` is a utility for retrying operations that may fail transiently and succeed on a subsequent attempt.

## Main features

- wall-clock duration budget controls when to give up
- `Policy` interface for backoff delays (`fixed`, `exponential`)
- `retryOn` predicate for filtering retryable throwables
- `onRetry` callbacks for logging and observability
- inspect failures via `Result`
- throw the last failure with earlier failures suppressed via `runAndThrow()`

## Example

```java
AtomicInteger counter = new AtomicInteger(0);
Retry.Result result = Retry.of(Policy.fixed(Duration.ofMillis(100), Duration.ofSeconds(1)))
        .onRetry((attempt, cause) -> System.out.println(
                "Retry attempt " + attempt + " after: " + cause.getMessage()))
        .run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("flaky failure");
            }
        });

assertThat(result.isPass()).isTrue();
assertThat(result.getAttemptCount()).isEqualTo(3);
```

## Duration budget

A `Retry` runs attempts until the wall-clock duration budget is exhausted. The budget is defined by the `Policy`'s `getMaximumDuration()` method. After each failed attempt, if the elapsed time has exceeded the budget, no further attempts are made.

The first attempt always runs regardless of the budget. A zero budget (`Duration.ZERO`) means one attempt with no retries.

## Backoff policies

The `Policy` interface defines both the delay schedule between attempts and the total duration budget. Two built-in policies are provided:

### Fixed (linear)

`Policy.fixed(initialDelay, maximumDuration)` escalates linearly. Each retry waits `initialDelay * attempt`, capped at the remaining budget.

| Attempt | `fixed(100ms, 1s)` |
|---------|---------------------|
| 1 fails | wait 100ms          |
| 2 fails | wait 200ms          |
| 3 fails | wait 300ms          |
| 4 fails | wait 400ms (capped at remaining budget) |
| ...     | budget exhausted    |

```java
Retry.of(Policy.fixed(Duration.ofMillis(100), Duration.ofSeconds(1)));
```

### Exponential

`Policy.exponential(initialDelay, maximumDuration)` doubles the delay each attempt, capped at the remaining budget.

| Attempt | `exponential(100ms, 5s)` |
|---------|--------------------------|
| 1 fails | wait 100ms               |
| 2 fails | wait 200ms               |
| 3 fails | wait 400ms               |
| 4 fails | wait 800ms               |
| 5 fails | wait 1.6s                |
| 6 fails | wait 3.2s (capped at remaining budget) |
| ...     | budget exhausted         |

```java
Retry.of(Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)));
```

### No delay

To retry with no delay between attempts, use `Policy.fixed(Duration.ZERO, maximumDuration)`. This retries as fast as possible until the budget is exhausted.

### Custom policies

`Policy` is an interface — implement `waitDuration(int attempt, Throwable cause)` and `getMaximumDuration()` for custom strategies such as jittered exponential backoff:

```java
Retry.of(new Policy() {
    @Override
    public Duration waitDuration(int attempt, Throwable cause) {
        Duration base = Duration.ofMillis(100).multipliedBy(1L << (attempt - 1));
        double jitter = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
        return Duration.ofMillis((long) (base.toMillis() * jitter));
    }

    @Override
    public Duration getMaximumDuration() {
        return Duration.ofSeconds(30);
    }
});
```

## Retry predicate

By default, `Retry` retries on any throwable except unrecoverable errors. Use `retryOn(Predicate<Throwable>)` to narrow which exceptions are retryable:

```java
Retry.of(Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(30)))
        .retryOn(t -> t instanceof IOException)
        .run(() -> callExternalApi());
```

When the predicate returns `false`, the retry sequence stops immediately and the result reflects the failure.

## Retry callbacks

Register callbacks with `onRetry(BiConsumer<Integer, Throwable>)` to observe retry attempts. The callback receives the 1-based next attempt number and the throwable from the failed attempt. Multiple callbacks can be registered and are invoked in registration order.

```java
Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)))
        .onRetry((attempt, cause) -> log.warn("Attempt {} failed: {}", attempt, cause.getMessage()))
        .onRetry((attempt, cause) -> metrics.incrementRetryCount())
        .run(() -> callExternalApi());
```

## Throwing aggregated failures

`runAndThrow()` throws the last captured exception when the budget is exhausted. Earlier exceptions are added as suppressed exceptions in attempt order.

```java
Retry.of(Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
        .runAndThrow(() -> callExternalApi());
```

## Re-running and clearing

By default, a `Retry` can only be run once. Calling `run()` a second time throws `IllegalStateException`.

- **`reset()`** resets the execution state so the same configuration can be re-run. Retry predicate and callbacks are preserved.

```java
Retry retry = Retry.of(Policy.fixed(Duration.ofMillis(100), Duration.ofSeconds(1)))
        .onRetry((attempt, cause) -> System.out.println("retry " + attempt));

Retry.Result result = retry.run(() -> callExternalApi());
// ... inspect result ...

retry.reset();                  // preserved configuration can now be re-run
Retry.Result result2 = retry.run(() -> callExternalApi());
```

- **`clear()`** removes all registered callbacks and resets the execution state. Use this to reuse the same instance from scratch.

```java
Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)))
        .onRetry((attempt, cause) -> System.out.println("retry " + attempt));

retry.run(() -> callExternalApi());
retry.clear();                  // callbacks removed, can add new ones
retry.onRetry((attempt, cause) -> log.info("retry " + attempt)).run(() -> callExternalApi());
```

## Fatal errors

`OutOfMemoryError` and `StackOverflowError` are **not retried**. They propagate immediately and no further attempts are made. Other `Error` subclasses are captured and may be retried if the `retryOn` predicate allows them.

## Thread safety

`Retry` is **not thread-safe**. Instances must not be shared across threads or accessed concurrently. Typical usage confines a `Retry` instance to a single method call or lifecycle scope.

## Relationship to `Direct`

A common pattern is using `Retry` inside a `Direct` action's runnable callback to handle transient failures in test logic:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Direct.builder("flaky-api-test")
            .runnable(context -> {
                Retry.of(Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
                        .retryOn(t -> t instanceof IOException)
                        .runAndThrow(() -> callExternalApi());
            })
            .build();
}
```

See the Retry example under `examples/src/main/java/examples/retry/`.
