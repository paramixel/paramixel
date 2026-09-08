---
title: Result
description: Result returned by a Paramixel run.
---

# Result

`Result` is returned from `Runner.run(...)`.

It exposes:

- the optional root `Descriptor` through `descriptor()`
- aggregate outcome predicates: `isPassed()`, `isFailed()`, `isSkipped()`, and `isAborted()`
- aggregate `startedAt()` and `completedAt()` timestamps

Use it to decide whether a programmatic run should fail a surrounding tool:

```java
var result = Runner.defaultRunner().run(action);
if (result.isFailed()) {
    throw new AssertionError("Paramixel run failed");
}
```

For process exit codes, prefer `Runner.runAndReturnExitCode(...)` or `Runner.runAndExit(...)`.
