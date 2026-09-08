---
title: Status
description: Execution statuses in Paramixel.
---

# Status

`Status` is a value object representing an action or aggregate outcome.

## Values

- `Status.PENDING`
- `Status.RUNNING`
- `Status.PASSED`
- `Status.FAILED`
- `Status.SKIPPED`
- `Status.ABORTED`

## Factories

```java
Status.failed("message");
Status.failed("message", throwable);
Status.skipped("message");
Status.aborted("message");
```

## Predicates

Use `isPending()`, `isRunning()`, `isPassed()`, `isFailed()`, `isSkipped()`, and `isAborted()`.

## Aggregation

`Status.aggregate(List<Descriptor>)` computes aggregate tree status. Failures dominate normal success; skipped and aborted outcomes are represented explicitly and can be promoted to failing exit codes with configuration.
