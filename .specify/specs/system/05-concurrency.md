# Paramixel -- Concurrency

## Overview

Paramixel executes tests concurrently using Java 21 virtual threads, constrained by a
three-level semaphore system. This spec defines the concurrency model, parallelism
configuration, and runtime behavior.

## Virtual Thread Executor

All parallel work MUST be submitted to `Executors.newVirtualThreadPerTaskExecutor()`.
Platform thread pools MUST NOT be introduced. The executor is owned by
`ParamixelExecutionRuntime`, which implements `AutoCloseable`.

**Shutdown behavior:** When `ParamixelExecutionRuntime.close()` is called, the executor
MUST shut down within 30 seconds. If tasks do not complete within this timeout, they are
interrupted and the executor terminates.

## Three-Level Concurrency Limiter

`ParamixelConcurrencyLimiter` uses three fair `Semaphore` instances:

| Level | Permits | Purpose |
|---|---|---|
| Total | `cores * 2` | Hard ceiling on all concurrent work |
| Class | `cores` | Limits parallel test classes |
| Argument | `cores` | Limits parallel argument buckets within a class |

Where `cores` = `Runtime.getRuntime().availableProcessors()`.

All semaphores use fair ordering (`new Semaphore(permits, true)`).

### First-Argument Optimization

The first argument in each class MUST always run inline (never dispatched to the executor).
This guarantees progress even when all semaphore slots are saturated.

## Parallelism Configuration

### Global Parallelism (`paramixel.parallelism`)

Controls the maximum number of concurrent test classes. Defaults to
`Runtime.getRuntime().availableProcessors()`.

**Configuration precedence** (highest to lowest):
1. JUnit Platform Configuration Parameters (e.g., `-Dparamixel.parallelism=4`)
2. Properties file (`paramixel.properties` in project root)
3. Default value (`availableProcessors()`)

### Per-Class Parallelism (`ArgumentsCollector.setParallelism()`)

Individual test classes MAY specify their own parallelism limit via `setParallelism(int)`.
This value represents the maximum concurrency for argument buckets within that class.

**Constraint:** The effective parallelism for any class MUST NOT exceed the global
`paramixel.parallelism` setting. If a class requests `setParallelism(8)` and
`paramixel.parallelism=4`, the effective parallelism is 4.

The per-class parallelism defaults to `max(1, availableProcessors())`.

### Sequential Execution Trigger

When ANY `@Paramixel.Test` method in a class has `@Paramixel.Order`, ALL test methods for
each argument bucket in that class MUST execute sequentially, regardless of the configured
parallelism. See `04-lifecycle.md` for ordering rules.

## Configuration Properties

| Property Key | Source | Default | Description |
|---|---|---|---|
| `paramixel.parallelism` | Properties file or JUnit config params | `availableProcessors()` | Global max concurrent test classes |

### Maven Plugin Parallelism Parameter

The Maven plugin exposes `paramixel.parallelism` as a Mojo parameter. When set, it is
passed as a JUnit Platform configuration parameter and takes highest precedence.
See `07-maven-plugin.md`.
