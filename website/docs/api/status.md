---
title: Status
description: Terminal and in-flight kinds, severity ordering, and reasons.
---

# Status

```java
Status status = Status.failed("assertion failed", cause);
status.kind();        // FAILED
status.message();     // Optional[assertion failed]
status.throwable();   // Optional[cause]
```

| Kind | Terminal | Meaning |
| --- | --- | --- |
| `PENDING` | no | Compiled but not admitted |
| `RUNNING` | no | Execution started |
| `PASSED` | yes | Every required phase succeeded |
| `FAILED` | yes | Operation, runtime validation, timeout, or finalizer failed |
| `SKIPPED` | yes | Condition false, dependency unsatisfied, selection, or stop policy |
| `ABORTED` | yes | Assumption or precondition abort |
| `CANCELED` | yes | Run cancellation, shutdown, or timeout-driven abortion |

`ABORTED` and `CANCELED` are intentionally distinct: an assumption failure is a test verdict, while cancellation means
the task never produced one.

## Severity

`Status.mostSevere(collection)` implements `FAILED > CANCELED > ABORTED > SKIPPED > PASSED`. An empty collection
aggregates to `PASSED`.

## Exceptions and statuses

| Thrown | Recorded as |
| --- | --- |
| Any checked exception, `RuntimeException`, assertion failure | `FAILED` |
| `AbortException.abort(…)` | `ABORTED` |
| `SkipException.skip(…)` | `SKIPPED` |
| `FailException.fail(…)` | `FAILED` |
| Cancellation-driven interruption | `CANCELED` |
| Timeout expiry | `FAILED` with an explicit timeout reason |
