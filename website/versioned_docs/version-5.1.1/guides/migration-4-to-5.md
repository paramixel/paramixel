---
title: Migration 4.x to 5.x
description: Breaking API and behavior changes from 4.x to 5.x.
---

# Migration 4.x to 5.x

This guide covers the major API changes when migrating from Paramixel 4.x to 5.x.

## Package rename

| 4.x | 5.x |
| --- | --- |
| `org.paramixel.core` | `org.paramixel.api` |
| `org.paramixel.core.action` | `org.paramixel.api.action` |
| `org.paramixel.core.exception` | `org.paramixel.api.exception` |
| `org.paramixel.core.support` | `org.paramixel.api.support` |

## Removed classes

| Removed | Replacement |
| --- | --- |
| `Factory` | `Runner` — use `Runner.defaultRunner()` instead of `Factory.defaultRunner()` |
| `Flow` | `Lifecycle` — same before/body/after pattern, new spec API |
| `Noop` | Removed — use `Step.of("name", ctx -> {})` for a no-op action |
| `Context` | `Context` — moved to `org.paramixel.api.action`; provides configuration, listener, descriptor, instance access, status setters, and child scheduling |
| `Store` | Removed — use `Instance` for per-execution state, or `Context#instance(Class)` |

## Action API changes

| 4.x | 5.x |
| --- | --- |
| `Step.builder(name).runnable(ctx -> {}).build()` | `Step.of(name, ctx -> {})` |
| `Flow.builder(name).before(action).child(action).after(action).build()` | `Lifecycle.of(name).before(action).child(action).after(action).resolve()` |
| `Flow.Policy` | Removed — use `Lifecycle.of(name).independent()` or `.dependent()` |
| `Flow.ChildMode.INDEPENDENT` | `Lifecycle.of(name).independent()` |
| `Flow.ChildMode.DEPENDENT` | `Lifecycle.of(name).dependent()` (default) |
| `Noop.of(name)` | `Step.of(name, ctx -> {})` |
| `Parallel.builder(name).child(action).build()` | `Parallel.of(name).child(action).resolve()` |
| `Action.getName()` | `Action.name()` |
| Custom actions implement only `getName()`, `execute()` | Custom actions must also implement `kind()` |

## New action types

| Type | Description |
| --- | --- |
| `Sequential` | Ordered dependent or independent children without before/after |
| `Instance` | Factory-created instance with automatic lifecycle and `AutoCloseable` teardown |
| `Static` | Instance-free before/body/after lifecycle |

## Context API changes (5.x)

| 4.x `Context` | 5.x `Context` |
| --- | --- |
| `context.getConfiguration()` | `context.configuration()` |
| `context.getStore()` | Removed — use `Instance` or `context.instance(Class)` |
| `context.getListener()` | `context.listener()` |
| `context.getAncestor(path)` | Removed — use `Instance` for state propagation |
| `context.runAsync(action)` | Removed — use `Parallel` for concurrent execution |
| `context.createChild()` | Removed — handled by framework composites |

## Runner API changes

| 4.x | 5.x |
| --- | --- |
| `Factory.defaultRunner()` | `Runner.defaultRunner()` |
| `Factory.defaultRunner().runAndExit(action)` | `Runner.defaultRunner().runAndExit(action)` |
| `Runner.run(Action)` | `Runner.run(Action)` (unchanged) |
| `Runner` implements `AutoCloseable` | `Runner` no longer implements `AutoCloseable` |

## Listener API changes

| 4.x | 5.x |
| --- | --- |
| `Listener.runStarted(Runner)` | `Listener.onRunStarted()` |
| `Listener.beforeAction(Result)` | `Listener.onBeforeExecution(Descriptor)` |
| `Listener.actionThrowable(Result, Throwable)` | Removed — check `descriptor.metadata().throwable()` in `onAfterExecution` |
| `Listener.afterAction(Result)` | `Listener.onAfterExecution(Descriptor)` |
| `Listener.runCompleted(Runner, Result)` | `Listener.onRunCompleted(Result)` |
| `Listener.close()` | Removed — implement `AutoCloseable` yourself if needed |

## Result API changes

| 4.x | 5.x |
| --- | --- |
| `Result.getStatus().isPass()` | `result.status().isPassed()` |
| `Result.getStatus().isFailure()` | `result.status().isFailed()` |
| `Result.getRunDuration()` | `result.descriptor().get().metadata().runDuration()` |
| `Result.getParent()` | Removed — results support downward-only navigation |
| `Result.getStore()` | Removed |

## Configuration changes

| 4.x | 5.x |
| --- | --- |
| `Configuration.RUNNER_PARALLELISM` | `Configuration.RUNNER_PARALLELISM` (unchanged) |
| `Configuration.REPORT_FILE` | `Configuration.REPORT_FILE` (unchanged) |
| `Configuration.FAILURE_ON_SKIP` | `Configuration.FAILURE_ON_SKIP` (unchanged) |

New configuration keys in 5.x:

| Key | Description |
| --- | --- |
| `paramixel.scheduler.queue.capacity` | Max scheduler-ready tasks (default 1024) |
| `paramixel.failureOnAbort` | Whether ABORTED produces failing exit code (default true) |
| `paramixel.ansi` | ANSI output control (`true`, `false`, `auto`) |
| `paramixel.failIfNoTests` | Fail when no action factories are discovered |

## Resolver removal

`Resolver` is removed. Discovery is now an internal concern of `Runner`. Use the `Runner` methods that accept a `Selector`:

```java
Runner runner = Runner.defaultRunner();
Optional<Result> result = runner.run(selector);   // resolve + execute
int exitCode = runner.run();                       // discover all + execute
int exitCode = runner.runAndReturnExitCode(selector);
runner.runAndExit(selector);
```

## New: AnnotationResolver

5.x introduces `AnnotationResolver` for resolving `@Paramixel.Id` methods:

```java
AnnotationResolver<MyTest> resolver = AnnotationResolver.create(MyTest.class);
Action<MyTest> login = resolver.byId("login");
```

## New: Retry and CleanUp

5.x moves `Retry` and `CleanUp` to `org.paramixel.api.support`:

```java
import org.paramixel.api.support.Retry;
import org.paramixel.api.support.CleanUp;
```

## Quick migration checklist

1. Update package imports from `org.paramixel.core` to `org.paramixel.api`
2. Replace `Factory` with `Runner`
3. Replace `Flow` with `Lifecycle`
4. Replace `Noop` with `Step.of(name, ctx -> {})`
5. Replace `Step.builder(name).runnable(...).build()` with `Step.of(name, ...)`
6. Replace `Parallel.builder(name).child(...).build()` with `Parallel.of(name).child(...).resolve()`
7. Remove `Parallel.Builder.executorService(...)` and `Parallel.Builder.scheduler(...)` calls — 5.x `Parallel.Spec` uses `parallelism(int)` instead
8. Update `Context` import to `org.paramixel.api.action.Context`
9. Remove `Store` usage — use `Instance` for state propagation
10. Replace `Flow.Policy` with `.independent()` / `.dependent()`
11. Update `Listener` method names and signatures
12. Replace `isPass()` with `isPassed()`, `isFailure()` with `isFailed()`
13. Replace `getRunDuration()` with `result.descriptor().get().metadata().runDuration()`
14. Replace `Action.getName()` with `Action.name()`
15. Implement `kind()` on all custom `Action` implementations
16. Add `paramixel.properties` for classpath configuration if needed
