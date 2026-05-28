---
title: Status
description: Execution statuses in Paramixel.
---

# Status

`org.paramixel.api.Status` is the final class representing the execution status of an action.

## Values

| Status | Meaning |
| --- | --- |
| `PENDING` | Action has not yet started execution |
| `RUNNING` | Action is currently executing |
| `PASSED` | Completed without errors or exceptions |
| `FAILED` | Completed with a thrown exception or error |
| `SKIPPED` | Deliberately skipped via `SkipException` |
| `ABORTED` | Failed precondition via `AbortedException` |

## Predicate methods

```java
Status status = result.status();

status.isPending();  // true for PENDING
status.isRunning();  // true for RUNNING
status.isPassed();   // true for PASSED
status.isFailed();   // true for FAILED
status.isSkipped();  // true for SKIPPED
status.isAborted();  // true for ABORTED
status.isTerminal(); // true for PASSED, FAILED, SKIPPED, ABORTED
```

`message()` and `throwable()` are methods on the `Status` class, providing optional detail about the status.

## Instance methods

| Method | Return | Description |
| --- | --- | --- |
| `name()` | `String` | Returns the status name (`"PENDING"`, `"RUNNING"`, `"PASSED"`, `"FAILED"`, `"SKIPPED"`, `"ABORTED"`) |
| `message()` | `Optional<String>` | Optional detail message about the status |
| `throwable()` | `Optional<Throwable>` | Optional throwable associated with the status |

## Factory methods

Terminal statuses with detail can be created using named factory methods. A message is always required.

| Method | Description |
| --- | --- |
| `Status.failed(String message)` | Creates a `FAILED` status with a message |
| `Status.failed(String message, Throwable throwable)` | Creates a `FAILED` status with a message and throwable |
| `Status.skipped(String message)` | Creates a `SKIPPED` status with a message |
| `Status.skipped(String message, Throwable throwable)` | Creates a `SKIPPED` status with a message and throwable |
| `Status.aborted(String message)` | Creates an `ABORTED` status with a message |
| `Status.aborted(String message, Throwable throwable)` | Creates an `ABORTED` status with a message and throwable |

```java
// In a custom action:
context.setStatus(Status.failed("connection refused"));
context.setStatus(Status.failed("timeout", new TimeoutException()));
context.setStatus(Status.skipped("not applicable"));
context.setStatus(Status.aborted("precondition failed"));
```

Canonical statuses (`PENDING`, `RUNNING`, `PASSED`, `FAILED`, `SKIPPED`, `ABORTED`) are used directly and do not carry message or throwable detail.

## Static methods

| Method | Description |
| --- | --- |
| `Status.aggregate(List<Descriptor>)` | Computes aggregate status from child descriptors. Severity ordering: `FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`. |
| `Status.fromThrowable(Throwable)` | Maps `AbortedException` → `ABORTED`, `SkipException` → `SKIPPED`, `FailException` → `FAILED`. Rethrows unrecoverable errors; restores interrupt flag for `InterruptedException`. |

## Aggregation rules

Composite actions compute their status from child responses:

1. If any child is `FAILED`, the parent is `FAILED`.
2. Else if any child is `ABORTED`, the parent is `ABORTED`.
3. Else if any child is non-terminal (`RUNNING` or `PENDING`), the parent is `RUNNING`.
4. Else if any child is `SKIPPED`, the parent is `SKIPPED`.
5. otherwise, the parent is `PASSED`.

## Exit code mapping

`Runner.runAndReturnExitCode()` maps statuses to exit codes:

| Status | Exit code | Configurable |
| --- | --- | --- |
| `PASSED` | 0 | No |
| `FAILED` | 1 | No |
| `SKIPPED` | 0 (or 1) | Yes — `paramixel.failureOnSkip` |
| `ABORTED` | 1 (or 0) | Yes — `paramixel.failureOnAbort` |
| `PENDING` | 1 | No |

## See also

- [Result](./result)
- [Exception Reference](./exception-reference)
- [Descriptor and Metadata](./descriptor-and-metadata)
