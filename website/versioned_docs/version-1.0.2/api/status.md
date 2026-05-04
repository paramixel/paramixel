---
title: Status
description: Action result states.
---

# Status

Each `Result` has a `Status`.

## States

- `STAGED` - action has not completed yet
- `PASS` - action completed successfully
- `FAIL` - action failed
- `SKIP` - action was skipped

## Methods

```java
boolean isStaged()
boolean isPass()
boolean isFailure()
boolean isSkip()
String getDisplayName()
Optional<String> getMessage()
Optional<Throwable> getThrowable()
```

## Common meanings

### PASS

- `Direct` returned normally
- all relevant child actions completed without failure or skip

### FAIL

- a `FailException` was thrown
- an unexpected exception was thrown
- a composite action computed failure from children
- `Lifecycle.after` failed

### SKIP

- a `SkipException` was thrown
- a strict action skipped remaining siblings after a failure
- `Lifecycle.before` skipped `main`
- a parent action explicitly skipped descendants

## Composite action notes

- `Sequential` runs all children and computes final status from them
- `StrictSequential` and `StrictRandomSequential` skip remaining children after the first failure
- `Parallel` waits for all children, then computes status
- `Lifecycle` can skip `main` and still run `after`
