---
title: API Reference
description: Quick reference for the public Paramixel API.
---

# API Reference

This page is a compact map of the v2.0.0 public API.

## Core types

### `Runner`

```java
interface Runner {
    static Builder builder();

    Map<String, String> getConfiguration();
    Listener getListener();
    Result run(Action action);
    int runAndReturnExitCode(Action action);
    int runAndReturnExitCode(Selector selector);
    void runAndExit(Action action);
    void runAndExit(Selector selector);

    final class Builder {
        Builder configuration(Map<String, String> properties);
        Builder listener(Listener listener);
        Builder executorService(ExecutorService executorService);
        Runner build();
    }
}
```

`Runner.run(Action)` returns a `Result`. A `Runner` can be reused — calling `run()` multiple times is safe and each call is independent. Owned executor services are created and shut down per invocation. External executors supplied via `executorService(...)` are not managed by the runner.

### `Factory`

```java
final class Factory {
    static Runner defaultRunner();
    static Listener defaultListener();
}
```

- `defaultRunner()` creates a `DefaultRunner` with default configuration and default listener
- `defaultListener()` returns `SafeListener` wrapping `CompositeListener(StatusListener, SummaryListener(TreeSummaryRenderer))`

### `Action`

```java
interface Action {
    String getId();
    String getName();
    Optional<Action> getParent();
    void setParent(Action parent);
    void addChild(Action child);
    List<Action> getChildren();
    Result execute(Context context);
    Result skip(Context context);
}
```

`getId()` returns a randomly generated 4-character string (a–z, A–Z) that uniquely identifies the action instance.

Action hierarchy:

```
Action (interface)
  └─ AbstractAction (abstract)
       ├─ LeafAction (abstract)
       │    ├─ Direct
       │    └─ Noop
       └─ BranchAction (abstract)
            ├─ Sequential
            ├─ DependentSequential
            ├─ RandomSequential
            ├─ DependentRandomSequential
            ├─ Parallel
            └─ Lifecycle
```

### `Context`

```java
interface Context {
    Map<String, String> getConfiguration();
    Optional<Context> getParent();
    Store getStore();
    Listener getListener();
    ExecutorService getExecutorService();
    Optional<Context> findAncestor(int levelUp);
    Context createChild();
}
```

`findAncestor(levelUp)` returns the current/ancestor context wrapped in `Optional`, but it throws if `levelUp` is negative or the ancestor does not exist.

### `Result`

```java
interface Result {
    Optional<Result> getParent();
    List<Result> getChildren();
    Action getAction();
    Status getStatus();
    Duration getElapsedTime();
    Duration getCumulativeElapsedTime();
}
```

Results form a tree that mirrors the action tree. `getCumulativeElapsedTime()` returns `getElapsedTime()` for leaf actions and the sum of children's cumulative elapsed times for branch actions.

### `Status`

```java
interface Status {
    boolean isStaged();
    boolean isPass();
    boolean isFailure();
    boolean isSkip();
    String getDisplayName();
    Optional<String> getMessage();
    Optional<Throwable> getThrowable();
}
```

Display names: `STAGED`, `PASS`, `FAIL`, `SKIP`. Use `isFailure()` (not `isFail()`).

### `Store`

```java
interface Store {
    interface Entry {
        String getKey();
        Value getValue();
        Value setValue(Value value);
    }

    int size();
    boolean isEmpty();
    boolean containsKey(String key);
    boolean containsValue(Value value);
    Optional<Value> get(String key);
    Optional<Value> put(String key, Value value);
    Optional<Value> remove(String key);
    void putAll(Store store);
    void clear();
    Set<String> keySet();
    Collection<Value> values();
    Set<Entry> entrySet();
    Value getOrDefault(String key, Value defaultValue);
    void forEach(BiConsumer<? super String, ? super Value> action);
    void replaceAll(BiFunction<? super String, ? super Value, ? extends Value> function);
    Optional<Value> putIfAbsent(String key, Value value);
    boolean remove(String key, Value value);
    boolean replace(String key, Value oldValue, Value newValue);
    Optional<Value> replace(String key, Value value);
    Optional<Value> computeIfAbsent(String key, Function<? super String, ? extends Value> mappingFunction);
    Optional<Value> computeIfPresent(String key, BiFunction<? super String, ? super Value, ? extends Value> remappingFunction);
    Optional<Value> compute(String key, BiFunction<? super String, ? super Value, ? extends Value> remappingFunction);
    Optional<Value> merge(String key, Value value, BiFunction<? super Value, ? super Value, ? extends Value> remappingFunction);
}
```

Every method that returns a store value returns `Optional<Value>`. All methods reject `null` keys and values with `NullPointerException`.

### `Value`

```java
final class Value {
    static Value of(Object value);
    Object get();
    boolean isType(Class<?> type);
    <T> T cast(Class<T> type);
}
```

`Value.of()` rejects `null`. Use `isType()` for type-checking without casting, `cast()` for typed access.

### `Listener`

```java
interface Listener {
    default void runStarted(Runner runner)
    default void beforeAction(Result result)
    default void actionThrowable(Result result, Throwable throwable)
    default void afterAction(Result result)
    default void skipAction(Result result)
    default void runCompleted(Runner runner, Result result)
}
```

All callbacks receive `Result` (not `Context` + `Action`). Use `result.getAction()` to access the action.

Built-in implementations in `org.paramixel.core.spi.listener`: `SafeListener`, `CompositeListener`, `StatusListener`, `SummaryListener`, `TreeSummaryRenderer`, `TableSummaryRenderer`.

### `Selector`

```java
final class Selector {
    static Builder builder();

    final class Builder {
        Builder packageMatch(String regex);
        Builder packageOf(Class<?> clazz);
        Builder classMatch(String regex);
        Builder classOf(Class<?> clazz);
        Builder tagMatch(String regex);
        Selector build();
    }
}
```

Only one location criterion (package or class) is allowed. Tag filter is orthogonal. Regex patterns use `Pattern.matcher().find()` (substring match). For exact match, anchor with `^...$`.

### `Resolver`

```java
final class Resolver {
    static Optional<Action> resolveActions();
    static Optional<Action> resolveActions(Selector selector);
    static Optional<Action> resolveActions(Map<String, String> configuration);
    static Optional<Action> resolveActions(Map<String, String> configuration, Selector selector);
}
```

Four overloads. No `ClassLoader`, `Predicate`, or `Composition` parameters. Discovered actions are always combined as `Parallel`.

### `Configuration`

```java
final class Configuration {
    static final String RUNNER_PARALLELISM = "paramixel.parallelism";
    static final String FAILURE_ON_SKIP = "paramixel.failureOnSkip";
    static final String PACKAGE_MATCH = "paramixel.match.package";
    static final String CLASS_MATCH = "paramixel.match.class";
    static final String TAG_MATCH = "paramixel.match.tag";

    static Map<String, String> classpathProperties();
    static Map<String, String> systemProperties();
    static Map<String, String> defaultProperties();
}
```

### `Version`

```java
final class Version {
    static String getVersion();
}
```

Loads from classpath resource `version.properties`, property key `version`.

## Annotations

### `@Paramixel.ActionFactory`

Marks a `public static` no-arg method that returns `Action`.

### `@Paramixel.Disabled`

Excludes a factory from discovery. Optional `value()` for a reason string.

### `@Paramixel.Tag`

Tags a factory for selective discovery. Repeatable via `@Paramixel.Tags`.

```java
@Paramixel.Tag("smoke")
@Paramixel.Tag("fast")
```

## Exception classes

All in `org.paramixel.core.exception`:

| Class | Description |
|---|---|
| `FailException` | Signal action failure |
| `SkipException` | Signal action skip |
| `CycleDetectedException` | Action graph contains a parent-child cycle |
| `DeadlockDetected` | Potential thread-starvation deadlock detected |
| `ConfigurationException` | Invalid configuration |
| `ResolverException` | Action discovery/resolution failure |

## Built-in actions

All in `org.paramixel.core.action`:

| Class | Extends | Description |
|---|---|---|
| `Direct` | `LeafAction` | Execute a callback |
| `Noop` | `LeafAction` | Do nothing and pass |
| `Sequential` | `BranchAction` | Run all children in order |
| `DependentSequential` | `BranchAction` | Stop on first failure |
| `RandomSequential` | `BranchAction` | Shuffled run-all |
| `DependentRandomSequential` | `BranchAction` | Shuffled fail-fast |
| `Parallel` | `BranchAction` | Concurrent execution |
| `Lifecycle` | `BranchAction` | before/main/after |