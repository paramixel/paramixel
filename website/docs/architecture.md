---
title: Architecture
description: How Paramixel discovery and execution work.
---

# Architecture

Paramixel has two main phases:

1. **Discovery** - optional classpath scanning for `@Paramixel.ActionFactory`
2. **Execution** - running an `Action` tree with a `Runner`

## Main packages

- `org.paramixel.core` - public API
- `org.paramixel.core.action` - built-in actions
- `org.paramixel.core.discovery` - `Resolver` and `Selector`
- `org.paramixel.core.listener` - built-in listener implementations
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

Methods annotated with `@Paramixel.Disabled` are excluded.
Invalid factories are not silently skipped; discovery throws `ResolverException`.

When resolving a single class, `resolveActionsFromClass()` walks the full superclass chain. Only the outermost (most-derived) method for any given signature is considered. If more than one `@ActionFactory` method is found across the hierarchy, discovery throws `ResolverException`.

Discovered actions are combined with either:

- `Resolver.Composition.PARALLEL` - default
- `Resolver.Composition.SEQUENTIAL`

## Execution

`Runner` builds the root execution context, notifies the configured listener, and invokes `Action.execute(Context)` on the root action.

Important details:

- `Runner.run(Action)` returns `void`
- results live on each action via `getResult()`
- contexts form a hierarchy that mirrors execution nesting
- attachments are scoped to individual contexts
- `Parallel` uses an `ExecutorService`; runner-created executors are `ThreadPoolExecutor` instances
- **A `Runner` can be reused — calling `run()` multiple times on the same instance is safe.** Each call creates fresh owned executor services (if no external executor was supplied) and shuts them down when the call completes. If an `ExecutorService` was provided via `Runner.builder().executorService(...)`, the runner uses it but does not shut it down.

## Lifecycle model

`Lifecycle` executes three child actions:

1. `before`
2. `main`
3. `after`

`after` always runs, even if `before` fails or skips and even if `main` fails or skips. If `before` does not pass, `main` is skipped recursively.

## Listener model

`Listener` callbacks are:

- `runStarted`
- `beforeAction`
- `actionThrowable`
- `afterAction`
- `runCompleted`

The default listeners are created through `Listener.defaultListener()` and `Listener.treeListener()`.

## Maven plugin

The Maven plugin:

- builds a test classloader from test output, main output, and test classpath entries
- resolves actions with `Resolver.resolveActions(testClassLoader)`
- executes them with `Runner`
- fails the build when the root action result is `FAIL`
