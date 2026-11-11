---
title: API Overview
description: The public surface of Paramixel 7.
---

# API Overview

The public API is `org.paramixel.api`, with sub-packages for exceptions, selectors, and support types.

| Type | Role |
| --- | --- |
| `Task` | The single immutable definition |
| `Tasks`, `TaskBuilder`, `SequentialBuilder`, `ParallelBuilder`, `RepeatBuilder` | Authoring |
| `Pool` | Logical concurrency budget (a value object) |
| `Key` | Typed key for secondary resources |
| `Context` | Read-only per-occurrence metadata and resources |
| `Runner` | Compile, schedule, report; one run at a time |
| `Result`, `TaskResult`, `PhaseResult`, `Failure` | Immutable outcome tree |
| `Status`, `ExecutionMode`, `TaskPhase` | Enumerations |
| `Listener`, `TaskEvent` | Event contract |
| `Paramixel` | Discovery annotations |
| `Launcher` | CLI entry point that owns `System.exit` |

## Minimal example

```java
Task definition = Tasks.sequential("Smoke tests")
        .step("Ping", () -> ping())
        .step("Version", () -> checkVersion())
        .build();

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
