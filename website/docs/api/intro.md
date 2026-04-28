---
id: intro
title: API Reference
description: Public API quick reference
---

# API Reference

This page provides a quick reference for all public API types in Paramixel.

## Packages

### org.paramixel.core

Main package containing core abstractions and utilities.

### org.paramixel.core.action

Action composition subpackage.

### org.paramixel.core.gui (optional)

GUI visualization subpackage.

## Complete Type Index

| # | Type | Package | Kind |
|---|------|---------|------|
| 1 | `Action` | `org.paramixel.core` | interface |
| 2 | `AbstractAction` | `org.paramixel.core.action` | abstract class |
| 3 | `Context` | `org.paramixel.core` | interface |
| 3 | `Runner` | `org.paramixel.core` | interface |
| 4 | `Result` | `org.paramixel.core` | interface |
| 5 | `Listener` | `org.paramixel.core` | interface |
| 6 | `Configuration` | `org.paramixel.core` | final class |
| 7 | `Resolver` | `org.paramixel.core` | final class |
| 8 | `Resolver.Composition` | `org.paramixel.core` | enum |
| 9 | `Selector` | `org.paramixel.core` | final class |
| 10 | `Paramixel` | `org.paramixel.core` | final class (annotation holder) |
| 11 | `Paramixel.ActionFactory` | `org.paramixel.core` | annotation |
| 12 | `Paramixel.Disabled` | `org.paramixel.core` | annotation |
| 13 | `Runner.Builder` | `org.paramixel.core` | static nested class |
| 14 | `Result.Status` | `org.paramixel.core` | enum |
| 15 | `FailException` | `org.paramixel.core` | exception class |
| 16 | `SkipException` | `org.paramixel.core` | exception class |
| 17 | `ConfigurationException` | `org.paramixel.core` | exception class |
| 18 | `Direct` | `org.paramixel.core.action` | final class |
| 19 | `Sequential` | `org.paramixel.core.action` | final class |
| 20 | `StrictSequential` | `org.paramixel.core.action` | final class |
| 21 | `RandomSequential` | `org.paramixel.core.action` | final class |
| 22 | `StrictRandomSequential` | `org.paramixel.core.action` | final class |
| 23 | `Parallel` | `org.paramixel.core.action` | final class |
| 24 | `Lifecycle` | `org.paramixel.core.action` | final class |
| 25 | `Executable` | `org.paramixel.core.action` | @FunctionalInterface |
| 26 | `GuiExecutionListener` | `org.paramixel.core.gui` | class |

## Annotations

### @Paramixel.ActionFactory

Marks a public static method that returns an `Action` tree.

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest", ...);
}
```

### @Paramixel.Disabled

Excludes an `@Paramixel.ActionFactory` method from discovery.

```java
@Paramixel.Disabled("Reason for disabling")
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest", ...);
}
```

## Interfaces

### Action

Interface representing a named unit of executable work.

**Key Methods:**
- `String id()` — Unique identifier (UUID)
- `String name()` — Display name
- `Optional<Action> parent()` — Parent in tree
- `List<Action> children()` — Child actions
- `Result execute(Context context)` — Execute this action
- `Result skip(Context context)` — Skip this action

### Context

Provides runtime state during execution.

**Methods:**
- `Optional<Context> parent()` — Parent context
- `Action action()` — Associated action
- `<T> Context setAttachment(T attachment)` — Set attachment (fluent)
- `<T> Optional<T> attachment(Class<T> type)` — Retrieve attachment as Optional
- `Optional<Object> removeAttachment()` — Clear attachment
- `Context createChild(Action child)` — Create child context
- `Result execute(Action child)` — Execute child synchronously
- `CompletableFuture<Result> executeAsync(Action child)` — Execute child asynchronously
- `void beforeAction(Context, Action)` — Notify listener before action
- `void afterAction(Context, Action, Result)` — Notify listener after action

### Runner

Coordinates action execution.

**Methods:**
- `Listener listener()` — Execution listener
- `default Map<String, String> configuration()` — Configuration
- `Result run(Action action)` — Execute as root
- `Result run(Context context)` — Execute existing context
- `Result run(Action action, Context parentContext)` — Execute as child

**Builder:**
- `static Builder builder()` — Create builder
- `Builder runner(Runner)` — Use existing runner
- `Builder listener(Listener)` — Set listener
- `Builder configuration(Map<String, String>)` — Set configuration
- `Runner build()` — Build executor

### Listener

Receives notifications during execution.

**Methods:**
- `default void planStarted(Runner runner, Action action)` — Called before execution plan starts
- `default void planCompleted(Runner runner, Result result)` — Called after execution plan completes
- `default void beforeAction(Context context, Action action)` — Before execution
- `default void afterAction(Context context, Action action, Result result)` — After execution

**Static Factories:**
- `static Listener defaultListener()` — Default console listener
- `static Listener treeListener()` — Tree-formatted console listener

## Classes

### AbstractAction

Base implementation for all concrete actions.

**Key Methods:**
- `protected abstract Result doExecute(Context context, Instant start) throws Throwable` — Extension point for subclasses

**Protected Static Utilities:**
- `static Duration durationSince(Instant start)` — Compute elapsed time
- `static Status computeStatus(List<Result> results)` — Compute status from children
- `static Throwable findFailure(List<Result> results)` — Find first failure in children
- `protected final void adopt(Action child)` — Establish parent-child relationship

### Direct

Leaf action that executes an `Executable`.

**Static Factory:**
- `static Direct of(String name, Executable executable)`

**Method:**
- `Executable executable()` — Wrapped callback

### Sequential

Composite that executes children sequentially.

**Key Behavior:**
- Executes children sequentially from first to last
- **All children execute regardless of failures**
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip

**Static Factories:**
- `static Sequential of(String name, List<Action> children)`
- `static Sequential of(String name, Action... children)`

**Method:**
- `List<Action> children()` — Child actions (unmodifiable)

### StrictSequential

Sequential action that stops on first failure.

**Key Behavior:**
- Executes children sequentially from first to last
- **Stops on first failure** — remaining children are reported as `SKIP`
- Skipped children still fire listener `beforeAction()` and `afterAction()` callbacks
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip

**Static Factories:**
- `static StrictSequential of(String name, List<Action> children)`
- `static StrictSequential of(String name, Action... children)`

**Method:**
- `List<Action> children()` — Child actions (unmodifiable)

### RandomSequential

Composite that executes children sequentially in random order.

**Key Behavior:**
- Executes children in random order
- **All children execute regardless of failures**
- Supports optional seed for reproducible ordering
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip

**Static Factories:**
- `static RandomSequential of(String name, List<Action> children)` — Unseeded
- `static RandomSequential of(String name, Action... children)` — Unseeded varargs
- `static RandomSequential of(String name, long seed, List<Action> children)` — Seeded
- `static RandomSequential of(String name, long seed, Action... children)` — Seeded varargs

**Methods:**
- `List<Action> children()` — Child actions (unmodifiable)
- `OptionalLong seed()` — The seed if provided, otherwise empty

### StrictRandomSequential

Random sequential action that stops on first failure.

**Key Behavior:**
- Executes children in random order
- **Stops on first failure** — remaining children are reported as `SKIP`
- Skipped children still fire listener `beforeAction()` and `afterAction()` callbacks
- Supports optional seed for reproducible ordering
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip

**Static Factories:**
- `static StrictRandomSequential of(String name, List<Action> children)` — Unseeded
- `static StrictRandomSequential of(String name, Action... children)` — Unseeded varargs
- `static StrictRandomSequential of(String name, long seed, List<Action> children)` — Seeded
- `static StrictRandomSequential of(String name, long seed, Action... children)` — Seeded varargs

**Methods:**
- `List<Action> children()` — Child actions (unmodifiable)
- `OptionalLong seed()` — The seed if provided, otherwise empty

### Parallel

Composite that executes children concurrently.

**Static Factories:**
- `static Parallel of(String name, List<Action> children)` — Unbounded
- `static Parallel of(String name, int parallelism, List<Action> children)` — Bounded
- `static Parallel of(String name, Action... children)` — Unbounded varargs
- `static Parallel of(String name, int parallelism, Action... children)` — Bounded varargs

**Methods:**
- `int parallelism()` — Max concurrent actions
- `List<Action> children()` — Child actions (unmodifiable)

### Lifecycle

Composite with setup/body/teardown phases.

**Static Factories:**
- `static Lifecycle of(String name, Action body)` — Body only
- `static Lifecycle of(String name, Executable setup, Action body)` — Setup + body
- `static Lifecycle of(String name, Action body, Executable teardown)` — Body + teardown
- `static Lifecycle of(String name, Executable setup, Action body, Executable teardown)` — Full

**Methods:**
- `Optional<Executable> setup()` — Setup callback
- `Action body()` — Body action
- `Optional<Executable> teardown()` — Teardown callback
- `List<Action> children()` — Returns `List.of(body)`

### Configuration

Loads configuration from classpath and system properties.

**Constants:**
- `static final String CONFIG_FILE_NAME = "paramixel.properties"`
- `static final String RUNNER_PARALLELISM = "paramixel.core.runner.parallelism"`

**Static Methods:**
- `static Map<String, String> classpathProperties()` — Classpath only
- `static Map<String, String> systemProperties()` — System properties only
- `static Map<String, String> defaultProperties()` — Merged with defaults

### Resolver

Discovers `@Paramixel.ActionFactory` methods via ClassGraph.

**Static Methods:**
- `static Optional<Action> resolveActions()` — All packages, parallel
- `static Optional<Action> resolveActions(Composition composition)` — All packages
- `static Optional<Action> resolveActions(String packageRegex)` — Specific package
- `static Optional<Action> resolveActions(String packageRegex, Composition composition)`
- `static Optional<Action> resolveActions(Predicate<String> packagePredicate)`
- `static Optional<Action> resolveActions(Predicate<String> packagePredicate, Composition composition)`
- `static Optional<Action> resolveActions(Selector selector)`
- `static Optional<Action> resolveActions(Selector selector, Composition composition)`
- `static Optional<Action> resolveActions(ClassLoader classLoader)` — Custom classloader
- `static Optional<Action> resolveActions(ClassLoader classLoader, Composition composition)`
- `static Optional<Action> resolveActions(ClassLoader classLoader, Predicate<String> packagePredicate)`
- `static Optional<Action> resolveActions(ClassLoader classLoader, Predicate<String> packagePredicate, Composition composition)`
- `static Optional<Action> resolveActionsFromClass(Class<?> clazz)` — Single class

### Selector

Describes a classpath selection as a regex.

**Static Factories:**
- `static Selector byPackageName(String packageName)` — Match package and subpackages
- `static Selector byPackageName(Class<?> clazz)` — Match class package and subpackages
- `static Selector byClassName(String className)` — Match specific class
- `static Selector byClassName(Class<?> clazz)` — Match specific class

**Method:**
- `String regex()` — The regex pattern

### Result

Describes action outcome.

**Methods:**
- `Action action()` — The action that produced this result
- `Status status()` — PASS, FAIL, or SKIP
- `Duration timing()` — Execution duration
- `Optional<Throwable> failure()` — Failure cause
- `Optional<Result> parent()` — Parent result
- `List<Result> children()` — Child results

**Static Factories:**
- `static Result pass(Action action, Duration timing)` — Passing result
- `static Result fail(Action action, Duration timing, Throwable failure)` — Failing result
- `static Result skip(Action action, Duration timing)` — Skipped result
- `static Result skip(Action action, Duration timing, Throwable skipReason)` — Skipped result with reason
- `static Result of(Action action, Status status, Duration timing, Throwable failure, List<Result> children)` — Full result

## Enums

### Result.Status

Action outcome status.

**Values:**
- `PASS` — Action completed successfully
- `FAIL` — Action failed
- `SKIP` — Action was skipped

### Resolver.Composition

Action composition strategy.

**Values:**
- `SEQUENTIAL` — Compose actions in `Sequential`
- `PARALLEL` — Compose actions in `Parallel`

## Functional Interfaces

### Executable

Functional interface for action execution.

**Method:**
- `void execute(Context context) throws Throwable`

**Static Method:**
- `static Executable noop()` — No-op executable

## Exceptions

### FailException

Marks an action as failed.

**Convenience Methods:**
- `static void fail()` — Throw with no message
- `static void fail(String message)` — Throw with message
- `static void fail(String message, Throwable cause)` — Throw with message and cause

### SkipException

Marks an action as skipped.

**Convenience Methods:**
- `static void skip()` — Throw with no message
- `static void skip(String message)` — Throw with message
- `static void skip(String message, Throwable cause)` — Throw with message and cause

### ConfigurationException

Thrown when configuration loading fails.

## Javadoc

To generate Javadoc locally:

```bash
./mvnw javadoc:javadoc -pl core -am
```

Output: `core/target/site/apidocs/`

IDE integration: Most IDEs can navigate to source code directly from this reference.

## See Also

- [Action Composition](../usage/action-composition) - Using action types
- [Context](../usage/context) - Attachment API patterns
- [Discovery](../usage/discovery) - Resolver and Selector usage
