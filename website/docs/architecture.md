---
title: Architecture
description: How Paramixel discovery and execution work.
---

# Architecture

Paramixel has two main phases:

1. **Discovery** - optional classpath scanning for `@Paramixel.ActionFactory`
2. **Execution** - running an `Action` tree with a `Runner`

## Main packages

- `org.paramixel.core` - public API (`Action`, `Context`, `Result`, `Status`, `Runner`, `Store`, `Value`, `Factory`, `Version`, `Selector`, `Resolver`, `Listener`, `Configuration`)
- `org.paramixel.core.action` - built-in actions (`Direct`, `Noop`, `Container`, `Parallel`)
- `org.paramixel.core.exception` - exceptions (`FailException`, `SkipException`, `CycleDetectedException`, `DeadlockDetected`, `ConfigurationException`, `ResolverException`)
- `org.paramixel.core.spi` - service provider interfaces and defaults (`DefaultResult`, `DefaultStatus`, `DefaultStore`, `DefaultContext`, `DefaultRunner`)
- `org.paramixel.core.spi.listener` - built-in listener implementations (`SafeListener`, `CompositeListener`, `StatusListener`, `SummaryListener`, `TreeSummaryRenderer`)
- `org.paramixel.core.support` - support utilities such as `Cleanup`
- `org.paramixel.maven.plugin` - Maven integration

## Discovery

`Resolver` uses ClassGraph to scan classes and invoke discovered factory methods. Discovery is not purely a scanning operation — `@ActionFactory` methods are invoked via reflection, so any side effects in factory method bodies occur at discovery time.

A valid factory method must be:

- `public`
- `static`
- zero-argument
- annotated with `@Paramixel.ActionFactory`
- return an `Action`
- the only `@ActionFactory` method in its class hierarchy

Methods annotated with `@Paramixel.Disabled` are excluded. Factories can be tagged with `@Paramixel.Tag` for selective discovery.

Invalid factories are not silently skipped; discovery throws `ResolverException`.

When resolving a single class, `resolveActionsFromClass()` walks the full superclass chain. Only the outermost (most-derived) method for any given signature is considered. If more than one `@ActionFactory` method is found across the hierarchy, discovery throws `ResolverException`.

Discovered actions are always combined as a `Parallel` root.

## Execution

`Runner` builds the root execution context, notifies the configured listener, invokes `Action.execute(Context)` on the root action, and returns a `Result` tree.

### Pre-execution validation

Before execution, `DefaultRunner` runs two validators:

1. **`CycleLoopDetector`** - detects parent-child cycles in the action graph; throws `CycleDetectedException`
2. **`DeadlockDetector`** - detects nested `Parallel` configurations that would cause thread starvation; throws `DeadlockDetected`

### Runtime execution

- `Runner.run(Action)` returns a `Result`
- Results form a tree that mirrors the action tree
- Each `Result` has a `Status`, run duration, parent, and children
- `Context` provides `getStore()` for per-node state and `findAncestor()` for navigating the context hierarchy
- `Parallel` uses a `RoutingExecutorService` that routes root-level work to the runner executor and nested parallel work to the parallel executor, preventing thread starvation for typical configurations
- A `Runner` can be reused — calling `run()` multiple times on the same instance is safe. Each call creates fresh owned executor services (if no external executor was supplied) and shuts them down when the call completes. If an `ExecutorService` was provided via `Runner.builder().executorService(...)`, the runner uses it but does not shut it down.

## Action hierarchy

```
Action (interface)
  └─ AbstractAction (abstract)
       ├─ Direct — takes Executable callback, no children
       ├─ Noop — always passes, no children
       ├─ Container — ordered composition with optional setup and teardown
       └─ Parallel — concurrent with semaphore
```

## Container model

`Container` executes three ordered regions:

1. `before`
2. body children
3. `after`

`after` always runs, even if `before` fails or skips and even if a body child fails or skips. If `before` does not pass, body children are skipped recursively. Body child ordering and fail-fast behavior are controlled by `Container.Policy`.

## Listener model

`Listener` callbacks are:

- `runStarted(Runner runner)`
- `beforeAction(Result result)`
- `actionThrowable(Result result, Throwable throwable)`
- `afterAction(Result result)`
- `skipAction(Result result)`
- `runCompleted(Runner runner, Result result)`

The default listener is created through `Factory.defaultListener()`.

## Maven plugin

The Maven plugin:

- builds a test classloader from test output, main output, and test classpath entries
- resolves actions with `Resolver.resolveActions(configuration, selector)`
- executes them with `Runner`
- fails the build when the root action result is `FAIL`
