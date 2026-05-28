---
title: Exception Reference
description: Framework exceptions for controlling action outcomes and signaling configuration or runtime errors.
---

# Exception Reference

Paramixel defines seven exception types in `org.paramixel.api.exception`. Three are used by test authors to control action outcomes; four are thrown by the framework to signal configuration, discovery, or runtime problems.

## Action outcome exceptions

These exceptions are thrown deliberately inside action callbacks to control the recorded outcome.

### FailException

Signals that an action should be marked as **failed**. Distinct from an uncaught runtime exception — this is a deliberate, author-initiated failure.

```java
import org.paramixel.api.exception.FailException;
```

| Constructor / Method | Description |
| --- | --- |
| `new FailException(String message)` | Creates with a message |
| `FailException.fail()` | Throws with default message `"failed"` |
| `FailException.fail(String message)` | Throws with a custom message |

```java
if (result.isEmpty()) {
    FailException.fail("expected non-empty result");
}
```

### SkipException

Signals that an action should be marked as **skipped**. The runtime records the action as skipped and continues execution of remaining actions.

```java
import org.paramixel.api.exception.SkipException;
```

| Constructor / Method | Description |
| --- | --- |
| `new SkipException(String message)` | Creates with a message |
| `SkipException.skip()` | Throws with default message `"skipped"` |
| `SkipException.skip(String message)` | Throws with a custom message |

```java
if (!isEnvironmentAvailable()) {
    SkipException.skip("environment not available");
}
```

### AbortedException

Signals that an action should be marked as **aborted** due to a precondition or assumption failure. Distinct from a skip — the action could not run due to an unmet condition.

```java
import org.paramixel.api.exception.AbortedException;
```

| Constructor / Method | Description |
| --- | --- |
| `new AbortedException(String message)` | Creates with a message |
| `AbortedException.abort()` | Throws with default message `"aborted"` |
| `AbortedException.abort(String message)` | Throws with a custom message |

```java
if (!preconditionMet()) {
    AbortedException.abort("precondition not met");
}
```

## Outcome mapping

When thrown inside an action, the runtime maps these exceptions to the corresponding `Status`:

| Exception | Status |
| --- | --- |
| `FailException` | `FAILED` |
| `SkipException` | `SKIPPED` |
| `AbortedException` | `ABORTED` |
| Any other `Throwable` | `FAILED` |

## Framework exceptions

These exceptions are thrown by the framework and indicate configuration, discovery, or runtime problems.

### ConfigurationException

Signals that configuration is invalid, inconsistent, or cannot be loaded. Halts startup.

```java
import org.paramixel.api.exception.ConfigurationException;
```

| Constructor | Description |
| --- | --- |
| `new ConfigurationException(String message)` | Creates with a message |
| `new ConfigurationException(String message, Throwable cause)` | Creates with a message and cause |

### CycleDetectedException

Signals that the action hierarchy contains a cyclic parent-child relationship. Halts the test run before any actions execute.

```java
import org.paramixel.api.exception.CycleDetectedException;
```

| Constructor | Description |
| --- | --- |
| `new CycleDetectedException(String message)` | Creates with a message |

### ResolverException

Signals that action discovery or factory resolution failed during classpath scanning. Halts startup.

```java
import org.paramixel.api.exception.ResolverException;
```

| Constructor | Description |
| --- | --- |
| `new ResolverException(String message)` | Creates with a message |
| `new ResolverException(String message, Throwable cause)` | Creates with a message and cause |

### PolicyException

Signals that a `Retry.Policy` computation failed. The `Retry` loop catches this exception and treats the failed computation as zero, allowing the retry sequence to continue. Custom `Policy` implementations should throw `PolicyException` rather than unrelated `RuntimeException` subtypes.

```java
import org.paramixel.api.exception.PolicyException;
```

| Constructor | Description |
| --- | --- |
| `new PolicyException(String message)` | Creates with a message |
| `new PolicyException(String message, Throwable cause)` | Creates with a message and cause |

## Summary

| Exception | Package | Author-initiated | Runtime-thrown |
| --- | --- | --- | --- |
| `FailException` | `org.paramixel.api.exception` | Yes | No |
| `SkipException` | `org.paramixel.api.exception` | Yes | No |
| `AbortedException` | `org.paramixel.api.exception` | Yes | No |
| `ConfigurationException` | `org.paramixel.api.exception` | No | Yes |
| `CycleDetectedException` | `org.paramixel.api.exception` | No | Yes |
| `ResolverException` | `org.paramixel.api.exception` | No | Yes |
| `PolicyException` | `org.paramixel.api.exception` | No | Yes |
