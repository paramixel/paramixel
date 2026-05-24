---
title: Runner
description: Entry point for executing action trees.
---

# Runner

`org.paramixel.api.Runner` is the main entry point for launching an action tree.

## Creating a runner

```java
import java.util.Map;
import org.paramixel.api.Configuration;
// Default runner with framework configuration
Runner runner = Runner.defaultRunner();

// Custom runner
Runner runner = Runner.builder()
        .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "4")))
        .listener(new MyListener())
        .build();
```

## Running actions

```java
// Run a spec and get the result
Result result = runner.run(spec);

// Run with a selector
Optional<Result> result = runner.run(selector);

// Get exit code
int exitCode = runner.runAndReturnExitCode(spec);

// Run discovered actions from classpath
int exitCode = runner.run();

// Exit the JVM with the result
runner.runAndExit(spec);
```

## Runner methods

| Method | Description |
| --- | --- |
| `run(Spec<?>)` | Execute a spec tree; returns `Result` |
| `run(Selector)` | Resolve and execute with a selector; returns `Optional<Result>` (empty when no actions found) |
| `runAndReturnExitCode(Spec<?>)` | Execute and return `0` or `1` |
| `runAndReturnExitCode(Selector)` | Resolve, execute, and return exit code |
| `runAndExit(Spec<?>)` | Execute and call `System.exit()` |
| `runAndExit(Selector)` | Resolve, execute, and exit |
| `run()` | Discover from classpath and execute; returns `int` exit code |
| `configuration()` | The runner `Configuration` |
| `main(String[])` | Static entry point for CLI execution |

## Builder

```java
Runner runner = Runner.builder()
        .configuration(configuration)  // optional, defaults to Configuration.defaultConfiguration()
        .listener(myListener)         // optional, defaults to defaultListener
        .build();
```

When no explicit listener is supplied, the builder creates a default listener from the effective configuration. This ensures configuration-dependent report listeners are honored.

See [Configuration](./configuration) and [Listener](./listener) for details on the builder parameters.

## Concurrency

A single runner serializes `run(Spec<?>)` calls on the same instance to preserve listener lifecycle ordering. Different runner instances may execute concurrently when they operate on distinct action trees.

## Exit code rules

| Status | Exit code | Notes |
| --- | --- | --- |
| `PASSED` | 0 | |
| `SKIPPED` | 0 | `1` when `failureOnSkip=true` |
| `ABORTED` | 1 | `0` when `failureOnAbort=false` |
| `FAILED` | 1 | |
| `PENDING` | 1 | Non-terminal root; execution incomplete |
| No action found | 0 | `1` when `failIfNoTests=true` |

## Static main method

`Runner.main(String[])` discovers and executes actions using default configuration, then terminates the JVM:

```bash
java -cp myapp.jar org.paramixel.api.Runner
```

## See also

- [Result](./result)
- [Configuration](./configuration)
- [Listener](./listener)
