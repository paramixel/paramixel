---
title: API Overview
description: The public surface of Paramixel 7.
---

# API Overview

The public API is `org.paramixel.api`, with sub-packages for exceptions, selectors, and support types.

| Type | Role |
| --- | --- |
| `Task` | The single immutable definition |
| `TaskBuilder`, `SequentialBuilder`, `ParallelBuilder`, `RepeatBuilder` | Authoring |
| `Pool` | Logical concurrency budget (a value object) |
| `Context` | Per-occurrence metadata plus the scope's state store |
| `Runner` | Compile, schedule, report; one run at a time |
| `Result`, `TaskResult`, `PhaseResult`, `Failure` | Immutable outcome tree |
| `Status` | Immutable status value; `Status.Kind` is the enum |
| `ExecutionMode`, `TaskPhase` | Enumerations |
| `Listener`, `TaskEvent` | Event contract |
| `Paramixel` | Discovery annotations |
| `Launcher` | CLI entry point that owns `System.exit` |
| `Version` | The framework version string |

## Minimal example

```java
SequentialBuilder definition = Sequential.builder(SmokeTest.class, "Smoke tests")
        .step("ping")
        .step("version");

@Paramixel.Id("ping")
public static void ping(final Context context) {
    // ...
}

@Paramixel.Id("version")
public static void version(final Context context) {
    // ...
}

try (Runner runner = Runner.builder().build()) {
    Result result = runner.run(definition);
    System.exit(result.exitCode());
}
```

## Guarantees that shape the API

1. Definitions are immutable and lazy.
2. Configuration and plan validation happen before any user callback.
3. Pools are logical budgets, not thread pools.
4. Waiting never occupies a worker thread.
5. Timeouts are cooperative; non-quiescent work is reported honestly.
6. `Result` is a single immutable source of truth for console, file, and integration reporting.
