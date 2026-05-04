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
- `org.paramixel.core.action` - built-in actions (`Direct`, `Sequential`, `DependentSequential`, `RandomSequential`, `DependentRandomSequential`, `Parallel`, `Lifecycle`, `Noop`)
- `org.paramixel.core.exception` - exceptions (`FailException`, `SkipException`, `CycleDetectedException`, `DeadlockDetected`, `ConfigurationException`, `ResolverException`)
- `org.paramixel.core.spi` - service provider interfaces and defaults (`DefaultResult`, `DefaultStatus`, `DefaultStore`, `DefaultContext`, `DefaultRunner`)
- `org.paramixel.core.spi.listener` - built-in listener implementations (`SafeListener`, `CompositeListener`, `StatusListener`, `SummaryListener`, `TreeSummaryRenderer`, `TableSummaryRenderer`)
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
- Each `Result` has a `Status`, elapsed time, parent, and children
- `Context` provides `getStore()` for per-node state and `findAncestor()` for navigating the context hierarchy
- `Parallel` uses a `RoutingExecutorService` that routes root-level work to the runner executor and nested parallel work to the parallel executor, preventing thread starvation for typical configurations
- A `Runner` can be reused — calling `run()` multiple times on the same instance is safe. Each call creates fresh owned executor services (if no external executor was supplied) and shuts them down when the call completes. If an `ExecutorService` was provided via `Runner.builder().executorService(...)`, the runner uses it but does not shut it down.

## Action hierarchy

```
Action (interface)
  └─ AbstractAction (abstract)
       ├─ LeafAction (abstract) — no children
       │    ├─ Direct — takes Executable callback
       │    └─ Noop — always passes
       └─ BranchAction (abstract) — has children
            ├─ Sequential — runs all in order
            ├─ DependentSequential — stops on first failure
            ├─ RandomSequential — shuffled, runs all
            ├─ DependentRandomSequential — shuffled, stops on first failure
            ├─ Parallel — concurrent with semaphore
            └─ Lifecycle — before/primary/after
```

## Lifecycle model

`Lifecycle` executes three child actions:

1. `before`
2. `main`
3. `after`

`after` always runs, even if `before` fails or skips and even if `main` fails or skips. If `before` does not pass, `main` is skipped recursively.

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