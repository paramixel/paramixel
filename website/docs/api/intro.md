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
Optional<Action> getParent()
List<Action> getChildren()
void addChild(Action child)
void execute(Context context)
void skip(Context context)
Result getResult()
```

### `Runner`

```java
static Runner.Builder builder()
void run(Action action)
```

Builder methods:

```java
Builder configuration(Map<String, String> properties)
Builder listener(Listener listener)
Builder executorService(ExecutorService executorService)
Runner build()
```

`Runner.run(action)` returns `void`. Read the result from `action.getResult()`.

### `ConsoleRunner`

```java
static Optional<Result> run(Selector selector)
static int runAndReturnExitCode(Selector selector)
static void runAndExit(Selector selector)
static Result run(Action action)
static int runAndReturnExitCode(Action action)
static void runAndExit(Action action)
```

### `Context`

```java
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

### `Result`

```java
Status getStatus()
Duration getElapsedTime()
```

### `Status`

```java
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
Class<?> getType()
<T> Optional<T> to(Class<T> type)
```

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
```

`Resolver.Composition` values:

- `SEQUENTIAL`
- `PARALLEL`

### `Selector`

```java
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
```

### `Noop`

```java
Noop.of(String name)
```

### `Sequential`

```java
Sequential.of(String name, List<Action> children)
Sequential.of(String name, Action... children)
```

### `StrictSequential`

```java
StrictSequential.of(String name, List<Action> children)
StrictSequential.of(String name, Action... children)
```

### `RandomSequential`

```java
RandomSequential.of(String name, List<Action> children)
RandomSequential.of(String name, Action... children)
RandomSequential.of(String name, long seed, List<Action> children)
RandomSequential.of(String name, long seed, Action... children)
OptionalLong seed()
```

### `StrictRandomSequential`

```java
StrictRandomSequential.of(String name, List<Action> children)
StrictRandomSequential.of(String name, Action... children)
StrictRandomSequential.of(String name, long seed, List<Action> children)
StrictRandomSequential.of(String name, long seed, Action... children)
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
```

## Support

### `Cleanup`

Use `org.paramixel.core.support.Cleanup` to register cleanup tasks, closeables, and conditional cleanup steps. See [Cleanup](../usage/cleanup).
