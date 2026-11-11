---
title: Result
description: The outcome of a Paramixel run.
---

# Result

Every `Runner` entry point returns a `Result`.

It exposes:

- the optional root `Descriptor` through `descriptor()`
- the effective status through `status()`
- the process exit code through `exitCode()`
- the earliest start and latest completion instants through `startedAt()` and `completedAt()`

The effective status is the **root** descriptor's aggregated status after configuration promotion, not the most
severe status anywhere in the tree: a `Loop` that retried and then passed has `FAILED` iterations beneath a
`PASSED` root, and the run passed.

`exitCode()` is the single exit-code authority: `0` when the effective status is `PASSED`, `SKIPPED` or `ABORTED`
(after promotion), and `1` when it is `FAILED`:

```java
var result = Runner.builder().build().run(action);
if (result.exitCode() != 0) {
    throw new AssertionError("Paramixel run failed: " + result.status().message().orElse(""));
}
```

For a process exit code, make the exit visible at the call site:

```java
System.exit(Runner.builder().build().run().exitCode());
```
