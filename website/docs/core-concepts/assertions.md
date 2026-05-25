---
title: Assertions
description: Using assertion libraries with Paramixel.
---

# Assertions

Paramixel is not an assertion library. Use any Java assertion library inside `Step` callbacks. Thrown exceptions are captured as `FAILED` results.

## Using AssertJ

```java
import static org.assertj.core.api.Assertions.assertThat;

Step.of("user-age", user -> {
    assertThat(user.getAge()).isPositive();
});
```

## Using JUnit assertions

```java
import static org.junit.jupiter.api.Assertions.assertEquals;

Step.of("calculate", ctx -> {
    assertEquals(4, 2 + 2);
});
```

## Using plain assertions

```java
Step.of("simple", ctx -> {
    assert result == expected : "result mismatch";
});
```

## How failures propagate

When a `Step` callback throws an exception:

| Exception type | Result status |
| --- | --- |
| `SkipException` | `SKIPPED` |
| `AbortedException` | `ABORTED` |
| `FailException` | `FAILED` |
| `AssertionError` | `FAILED` |
| Any other `Throwable` | `FAILED` (throwable attached) |

See [Exception Reference](../api/exception-reference) for the full exception API.

The `VirtualMachineError` hierarchy is treated specially: `StackOverflowError` is captured as a normal failure; other `VirtualMachineError` subtypes (`OutOfMemoryError`, `InternalError`, etc.) are rethrown immediately.

## Custom failure messages

Use `FailException` for explicit failure with a message:

```java
import org.paramixel.api.exception.FailException;

Step.of("validate", ctx -> {
    if (!isValid()) {
        throw new FailException("validation failed: expected valid state");
    }
});
```

## Conditional skipping

Use `SkipException` to skip a test when preconditions are not met:

```java
import org.paramixel.api.exception.SkipException;

Step.of("requires-docker", ctx -> {
    if (!dockerAvailable()) {
        throw new SkipException("Docker is not available");
    }
    // test logic
});
```

## Conditional aborting

Use `AbortedException` when a precondition failure should be treated as more severe than a skip:

```java
import org.paramixel.api.exception.AbortedException;

Step.of("requires-database", ctx -> {
    if (!databaseConnected()) {
        throw new AbortedException("database connection failed");
    }
    // test logic
});
```
