---
title: Status
description: Action result status.
---

# Status

`Status` is an interface in v2.0.0. Use `isFailure()` instead of the old `isFail()`.

## Interface

```java
interface Status {
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

Status instances are created internally by the framework during action execution. You do not typically create them yourself. The SPI provides factory constructors for custom action implementations:

```java
// In org.paramixel.core.spi.DefaultStatus:
DefaultStatus(Kind kind)
DefaultStatus(Kind kind, String message)
DefaultStatus(Kind kind, Throwable throwable)
DefaultStatus(Kind kind, String message, Throwable throwable)
```