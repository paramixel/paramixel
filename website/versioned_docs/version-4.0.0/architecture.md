---
title: Architecture
description: How Paramixel discovery and execution work.
---

# Architecture

Paramixel has two main phases:

1. **Discovery** - optional classpath scanning for `@Paramixel.ActionFactory`
2. **Execution** - running an `Action` tree with a `Runner`

## Main packages

- `org.paramixel.core` - public API (`Action`, `AsyncScheduler`, `Context`, `Result`, `Status`, `Runner`, `Store`, `Factory`, `Version`, `Selector`, `Resolver`, `Listener`, `Configuration`)
- `org.paramixel.core.action` - built-in actions (`Direct`, `Noop`, `Container`, `Parallel`)
- `org.paramixel.core.exception` - exceptions (`FailException`, `SkipException`, `CycleDetectedException`, `ConfigurationException`, `ResolverException`)
- `org.paramixel.core.internal` - internal implementation classes and defaults (`DefaultResult`, `DefaultStatus`, `DefaultStore`, `DefaultContext`, `DefaultRunner`)
- `org.paramixel.core.internal.listener` - built-in listener implementations (`SafeListener`, `CompositeListener`, `StatusListener`, `SummaryListener`, `TreeSummaryRenderer`)
- `org.paramixel.core.support` - support utilities such as `Cleanup` and `Retry`
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

Methods annotated with `@Paramixel.Disabled` are excluded. Factories can be tagged with `@Paramixel.Tag` for selective discovery, and factory classes can use `@Paramixel.Priority` to influence discovery ordering.

Invalid factories are not silently skipped; discovery throws `ResolverException`.

When resolving a single class, `resolveActionsFromClass()` walks the full superclass chain. Only the outermost (most-derived) method for any given signature is considered. If more than one `@ActionFactory` method is found across the hierarchy, discovery throws `ResolverException`.

Discovered actions are always combined as a `Parallel` root. Before that root is built, actions are ordered by priority descending, then package name, action name, and class name.

## Run

`Runner` builds the root run context, notifies the configured listener, invokes `Action.run(Context)` on the root action, and returns a `Result` tree.

### Pre-run validation

Before execution, `DefaultRunner` validates the action tree structure:

1. **`CycleDetector`** - detects parent-child cycles in the action graph; throws `CycleDetectedException`

### Runtime execution

- `Runner.run(Action)` returns a `Result`
- Results form a tree that mirrors the action tree
- Each `Result` has a `Status`, run duration, parent, and children
- `Context` provides `getStore()` for per-node state and `getAncestor(path)` and `findAncestor(path)` for navigating the context hierarchy
- `Context.runAsync(...)` schedules additional actions through the effective scheduler for the current context
- `Parallel` uses the effective scheduler and can set a subtree-local scheduler with `Parallel.builder(...).scheduler(...)`
- `Runner` instances are not designed for concurrent use across action trees. A runner can execute multiple actions sequentially; each `run(Action)` call is independent.

## Action hierarchy

Context scoping is owned by composite actions. `Container` shares its context with `before` and `after` actions and creates an isolated child context for each body child. `Parallel` creates an isolated child context for each child. `Direct` uses whatever context it receives. Custom action implementations must apply their own context scoping in `run(Context)` and `skip(Context)`. Built-in actions are final framework primitives; implement `Action` directly, or extend `AbstractAction`, for custom behavior.

```
Action (interface)
  └─ AbstractAction (abstract)
       ├─ Direct — takes ThrowableRunnable callback, no children
       ├─ Noop — always passes, no children
       ├─ Container — ordered composition with optional setup and teardown
       └─ Parallel — concurrent children with an optional direct-child limit
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
- resolves actions with `Resolver.resolveActions(configuration)`
- executes them with `Runner`
- fails the build when the root action result is `FAIL`
