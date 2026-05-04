---
title: API Reference
description: Quick reference for the public Paramixel API.
---

# API Reference

This page is a compact map of the current public API.

## Core types

### `Action`

```java
String getId()
String getName()
```

`getId()` returns a randomly generated 4-character string (a–z, A–Z) that uniquely identifies the action instance.

```java
static Runner.Builder builder()
Map<String, String> getConfiguration()
Listener getListener()
void run(Action action)
```

Builder methods:

```java
Builder configuration(Map<String, String> properties)
Builder listener(Listener listener)
Builder executorService(ExecutorService executorService)
Runner build()
```

`Runner.run(action)` returns `void`. Results are read from `action.getResult()`.

A `Runner` can be reused — calling `run()` multiple times is safe and each call is independent. Owned executor services (the runner and parallel pools) are created and shut down per invocation. External executors supplied via `executorService(...)` are not managed by the runner.

### `ConsoleRunner`

```java
static Optional<Result> run(Selector selector)
static int runAndReturnExitCode(Selector selector)
static int runAndReturnExitCode(Selector selector, Map<String, String> configuration)
static void runAndExit(Selector selector)
static void runAndExit(Selector selector, Map<String, String> configuration)
static Result run(Action action)
static int runAndReturnExitCode(Action action)
static int runAndReturnExitCode(Action action, Map<String, String> configuration)
static void runAndExit(Action action)
static void runAndExit(Action action, Map<String, String> configuration)
```

### `Context`

```java
static Context of(Map<String, String> configuration, Listener listener, ExecutorService executorService)
Context createChild()
Optional<Context> getParent()
Map<String, String> getConfiguration()
Listener getListener()
ExecutorService getExecutorService()
Optional<Context> findContext(int level)
<T> Context setAttachment(T attachment)
Optional<Attachment> getAttachment()
Optional<Attachment> removeAttachment()
Optional<Attachment> findAttachment(int level)
```

`findContext(level)` returns the current/ancestor context wrapped in `Optional`, but it throws if `level` is negative or the ancestor does not exist.

### `Result`

```java
static Result of(Status status, Duration elapsedTime)
static Result staged()
static Result pass(Duration elapsedTime)
static Result fail(Duration elapsedTime, Throwable throwable)
static Result fail(Duration elapsedTime, String message)
static Result skip(Duration elapsedTime)
static Result skip(Duration elapsedTime, String message)
Status getStatus()
Duration getElapsedTime()
```

### `Status`

```java
static Status staged()
static Status pass()
static Status failure(Throwable throwable)
static Status failure(String message)
static Status failure(Throwable throwable, String message)
static Status failure()
static Status skip()
static Status skip(String message)
boolean isStaged()
boolean isPass()
boolean isFailure()
boolean isSkip()
String getDisplayName()
Optional<String> getMessage()
Optional<Throwable> getThrowable()
```

### `Attachment`

```java
static Attachment of(Object value)
Class<?> getType()
<T> Optional<T> to(Class<T> type)
```

`Attachment.getType()` returns `Object.class` when the wrapped value is `null`. Calling `setAttachment(null)` clears the attachment; `getAttachment()` will return `Optional.empty()`.

### `Listener`

```java
static Listener defaultListener()
static Listener treeListener()
default void runStarted(Runner runner, Action action)
default void runCompleted(Runner runner, Action action)
default void beforeAction(Context context, Action action)
default void actionThrowable(Context context, Action action, Throwable throwable)
default void afterAction(Context context, Action action, Result result)
```

## Discovery

### `Paramixel`

Annotations:

- `@Paramixel.ActionFactory`
- `@Paramixel.Disabled`

### `Resolver`

```java
Optional<Action> resolveActions()
Optional<Action> resolveActions(Resolver.Composition composition)
Optional<Action> resolveActions(String packageRegex)
Optional<Action> resolveActions(String packageRegex, Resolver.Composition composition)
Optional<Action> resolveActions(Predicate<String> packagePredicate)
Optional<Action> resolveActions(Predicate<String> packagePredicate, Resolver.Composition composition)
Optional<Action> resolveActions(ClassLoader classLoader)
Optional<Action> resolveActions(ClassLoader classLoader, Resolver.Composition composition)
Optional<Action> resolveActions(ClassLoader classLoader, Predicate<String> packagePredicate)
Optional<Action> resolveActions(ClassLoader classLoader, Predicate<String> packagePredicate, Resolver.Composition composition)
Optional<Action> resolveActions(Selector selector)
Optional<Action> resolveActions(Selector selector, Resolver.Composition composition)
Optional<Action> resolveActionsFromClass(Class<?> clazz)

`resolveActionsFromClass` walks the full superclass chain and throws `ResolverException` if more than one `@Paramixel.ActionFactory` method is found in the hierarchy.
```

`Resolver.Composition` values:

- `SEQUENTIAL`
- `PARALLEL`

### `Selector`

```java
String getRegex()
Selector.byPackageName(String packageName)
Selector.byPackageName(Class<?> clazz)
Selector.byClassName(String className)
Selector.byClassName(Class<?> clazz)
```

## Built-in actions

### `Direct`

```java
Direct.of(String name, Direct.Executable executable)
```

### `Lifecycle`

```java
Lifecycle.of(String name, Action before, Action main, Action after)
List<Action> getChildren()
```

### `Noop`

```java
Noop.of(String name)
```

### `Sequential`

```java
Sequential.of(String name, List<Action> children)
Sequential.of(String name, Action... children)
List<Action> getChildren()
```

### `StrictSequential`

```java
StrictSequential.of(String name, List<Action> children)
StrictSequential.of(String name, Action... children)
List<Action> getChildren()
```

### `RandomSequential`

```java
RandomSequential.of(String name, List<Action> children)
RandomSequential.of(String name, Action... children)
RandomSequential.of(String name, long seed, List<Action> children)
RandomSequential.of(String name, long seed, Action... children)
List<Action> getChildren()
OptionalLong seed()
```

### `StrictRandomSequential`

```java
StrictRandomSequential.of(String name, List<Action> children)
StrictRandomSequential.of(String name, Action... children)
StrictRandomSequential.of(String name, long seed, List<Action> children)
StrictRandomSequential.of(String name, long seed, Action... children)
List<Action> getChildren()
OptionalLong seed()
```

### `Parallel`

```java
Parallel.of(String name, List<Action> children)
Parallel.of(String name, int parallelism, List<Action> children)
Parallel.of(String name, Action... children)
Parallel.of(String name, int parallelism, Action... children)
Parallel.of(String name, ExecutorService executorService, List<Action> children)
Parallel.of(String name, ExecutorService executorService, Action... children)
int parallelism()
Optional<ExecutorService> executorService()
List<Action> getChildren()
```

## Support

### `Cleanup`

Use `org.paramixel.core.support.Cleanup` to register cleanup tasks, closeables, and conditional cleanup steps. See [Cleanup](../usage/cleanup).
