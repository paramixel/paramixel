---
title: Status
description: Action result status.
---

# Status

`Status` is an interface in v2.0.0. Use `isFailure()` instead of the old `isFail()`.

## Interface

```java
interface Status {
    static Status staged();
    static Status pass();
    static Status skip();
    static Status skip(String message);
    static Status failure();
    static Status failure(Throwable throwable);
    static Status failure(String message);

    boolean isStaged();
    boolean isPass();
    boolean isFailure();
    boolean isSkip();
    String getDisplayName();
    Optional<String> getMessage();
    Optional<Throwable> getThrowable();
}
```

## Display names

| Status | Display name |
|---|---|
| Staged | `STAGED` |
| Pass | `PASS` |
| Failure | `FAIL` |
| Skip | `SKIP` |

## Checking status

```java
if (result.getStatus().isPass()) { /* action passed */ }
if (result.getStatus().isFailure()) { /* action failed */ }
if (result.getStatus().isSkip()) { /* action was skipped */ }
```

## Kind enum

The SPI implementation `DefaultStatus` exposes a `Kind` enum:

```java
enum Kind { STAGED, PASS, FAILURE, SKIP }
```

This is internal to the SPI. Prefer using the interface methods (`isPass()`, `isFailure()`, `isSkip()`) for status checks.

## Creating status instances

Status instances are created internally by the framework during built-in action execution. Custom action implementations can use the public static factories:

```java
Status.staged();
Status.pass();
Status.skip();
Status.skip("reason");
Status.failure();
Status.failure(throwable);
Status.failure("message");
```
