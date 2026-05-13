---
title: API Reference
description: Quick reference for the public Paramixel API.
---

# API Reference

This page is a compact map of the 4.x public API.

## Core types

### `Runner`

```java
interface Runner extends AutoCloseable {
    static Builder builder();

    Map<String, String> getConfiguration();
    Listener getListener();
    Result run(Action action);
    Optional<Result> run(Selector selector);
    int runAndReturnExitCode(Action action);
    int runAndReturnExitCode(Selector selector);
    void runAndExit(Action action);
    void runAndExit(Selector selector);
    void close();

    final class Builder {
        Builder configuration(Map<String, String> properties);
        Builder listener(Listener listener);
        Runner build();
    }
}
```

`Runner.run(Action)` returns a `Result`. Use a single runner for one execution boundary, preferably with try-with-resources when report listeners may hold resources. Concurrent execution of the same or overlapping action trees is not supported.

### `Factory`

```java
final class Factory {
    static Runner defaultRunner();
    static Listener defaultListener();
    static Listener defaultListener(Map<String, String> configuration);
}
```

- `defaultRunner()` creates a `DefaultRunner` with default configuration and default listener
- `defaultListener()` returns `SafeListener` wrapping `CompositeListener(StatusListener, SummaryListener(TreeSummaryRenderer))`
- `defaultListener(configuration)` also includes a report listener when `paramixel.report.file` is configured

### `Action`

```java
interface Action {
    String getId();
    String getName();
    Result run(Context context);
    Result skip(Context context);
}
```

`getId()` returns a randomly generated 4-character string (a–z, A–Z) that uniquely identifies the action instance.

Built-in composite actions decide when to create child contexts. `Container` shares its context with `before` and `after` actions and creates an isolated child context for each body child. `Parallel` creates an isolated child context for each child. `Direct` uses whatever context it receives. Custom action implementations are responsible for applying their own context scoping in `run(Context)` and `skip(Context)`.

### `AbstractAction`

`AbstractAction` is a convenience base class for custom actions. It provides generated identifiers and name validation helpers, with final accessors for `getId()` and `getName()`.

`AbstractAction` does not wrap execution. Subclasses implement `run(Context)` and `skip(Context)` directly, including any null checks, listener callbacks, result construction, and context scoping behavior they require.

Built-in actions are final framework primitives and are not intended for subclassing. Implement `Action` directly, or extend `AbstractAction`, when you need custom action behavior.

### `CompositeAction`

```java
interface CompositeAction extends Action {
    List<Action> getChildren();
}
```

Implemented by `Container` and `Parallel`. Exposes the read-only child hierarchy.

Action hierarchy:

```
Action (interface)
  ├─ CompositeAction (interface, extends Action)
  │    ├─ Container
  │    └─ Parallel
  └─ AbstractAction (abstract, implements Action)
       ├─ Direct
       ├─ Noop
       ├─ Container (also implements CompositeAction)
       └─ Parallel (also implements CompositeAction)
```

Context scoping is owned by composite actions. `Container` shares its context with `before` and `after` actions and creates an isolated child context for each body child. `Parallel` creates an isolated child context for each child.

### `Context`

```java
interface Context {
    Context getParent();
    Optional<Context> findParent();
    Context getAncestor(String path);
    Optional<Context> findAncestor(String path);
    Map<String, String> getConfiguration();
    Listener getListener();
    CompletableFuture<Result> runAsync(Action action);
    Store getStore();
    Context createChild();
}
```

`runAsync(action)` schedules an action through the effective scheduler for the current context. `getParent()` returns the parent context directly and throws `AncestorNotFoundException` when this context is the root. `findParent()` returns the parent wrapped in `Optional`. `getAncestor(path)` navigates the context hierarchy using path semantics and throws `AncestorNotFoundException` when the path traverses beyond the root. `findAncestor(path)` is the safe variant that returns `Optional.empty()`. Path semantics: `"../"` for parent, `"../../"` for grandparent, `"/"` for root. Named segments and `.` segments are not allowed.

### `AsyncScheduler`

```java
interface AsyncScheduler {
    CompletableFuture<Result> runAsync(Action action, Context context);
}
```

Advanced SPI for custom `Parallel` subtree scheduling. Implementations must complete the returned future with the scheduled action result or complete it exceptionally.

### `Result`

```java
interface Result {
    static Result staged(Action action);
    static Result pass(Action action);
    static Result skip(Action action);
    static Result failure(Action action, Throwable throwable);
    static Result.Builder builder(Action action);

    Optional<Result> getParent();
    List<Result> getChildren();
    Action getAction();
    Status getStatus();
    Duration getRunDuration();

    final class Builder {
        Builder status(Status status);
        Builder runDuration(Duration runDuration);
        Builder child(Result child);
        Result build();
    }
}
```

Results form a tree that mirrors the action tree. `getRunDuration()` returns the wall-clock time of the full run or skip. Use `Result.builder(Action)` for custom result construction.

### `Status`

```java
interface Status {
    static Status staged();
    static Status pass();
    static Status skip();
    static Status skip(String message);
    static Status failure();
    static Status failure(Throwable throwable);
    static Status failure(String message);

    boolean isStaged();
    boolean isPass();
    boolean isFailure();
    boolean isSkip();
    String getDisplayName();
    Optional<String> getMessage();
    Optional<Throwable> getThrowable();
}
```

Display names: `STAGED`, `PASS`, `FAIL`, `SKIP`. Use `isFailure()` (not `isFail()`). The static factory methods are public API for custom action implementations.

### `Store`

```java
interface Store {
    interface Entry {
        String getKey();
        Object getValue();
        Object setValue(Object value);
    }

    int size();
    boolean isEmpty();
    boolean containsKey(String key);
    boolean containsValue(Object value);
    Optional<Object> get(String key);
    Optional<Object> put(String key, Object value);
    Optional<Object> remove(String key);
    void putAll(Store store);
    void clear();
    Set<String> keySet();
    Collection<Object> values();
    Set<Entry> entrySet();
    Object getOrDefault(String key, Object defaultValue);
    void forEach(BiConsumer<? super String, ? super Object> action);
    void replaceAll(BiFunction<? super String, ? super Object, ? extends Object> function);
    Optional<Object> putIfAbsent(String key, Object value);
    boolean remove(String key, Object value);
    boolean replace(String key, Object oldValue, Object newValue);
    Optional<Object> replace(String key, Object value);
    Optional<Object> computeIfAbsent(String key, Function<? super String, ? extends Object> mappingFunction);
    Optional<Object> computeIfPresent(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction);
    Optional<Object> compute(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction);
    Optional<Object> merge(String key, Object value, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction);
    <T> Optional<T> get(String key, Class<T> type);
    <T> Optional<T> remove(String key, Class<T> type);
    <T> T getOrDefault(String key, Class<T> type, T defaultValue);
    boolean isType(String key, Class<?> type);
}
```

Every method that returns a store value returns `Optional<Object>`. Use `get(key, type)` for typed access. All methods reject `null` keys and values with `NullPointerException`.

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

Built-in implementations in `org.paramixel.core.internal.listener`: `SafeListener`, `CompositeListener`, `StatusListener`, `SummaryListener`, `TreeSummaryRenderer`.

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

Four overloads. No `ClassLoader`, `Predicate`, or `Composition` parameters. Discovered actions are always combined as `Parallel` after ordering by priority descending, then package name, action name, and class name.

### `Configuration`

```java
final class Configuration {
    static final String CONFIG_FILE_NAME = "paramixel.properties";
    static final String RUNNER_PARALLELISM = "paramixel.parallelism";
    static final String FAILURE_ON_SKIP = "paramixel.failureOnSkip";
    static final String PACKAGE_MATCH = "paramixel.match.package";
    static final String CLASS_MATCH = "paramixel.match.class";
    static final String TAG_MATCH = "paramixel.match.tag";
    static final String REPORT_FILE = "paramixel.report.file";

    static Map<String, String> classpathProperties();
    static Map<String, String> classpathProperties(ClassLoader classLoader);
    static Map<String, String> systemProperties();
    static Map<String, String> defaultProperties();
    static Map<String, String> defaultProperties(ClassLoader classLoader);
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

### `@Paramixel.Priority`

Orders discovered factory classes before the resolver-created root `Parallel` is built. Higher values are ordered earlier; the default is `0`.

```java
@Target(ElementType.TYPE)
@interface Priority {
    int value() default 0;
}
```

## Exception classes

All in `org.paramixel.core.exception`:

| Class | Description |
|---|---|
| `FailException` | Signal action failure |
| `SkipException` | Signal action skip |
| `CycleDetectedException` | Action graph contains a parent-child cycle |
| `ConfigurationException` | Invalid configuration |
| `ResolverException` | Action discovery/resolution failure |
| `AncestorNotFoundException` | Ancestor context does not exist |

## Built-in actions

All in `org.paramixel.core.action`:

| Class | Extends | Description |
|---|---|---|
| `Direct` | `AbstractAction` | Final action that runs a callback |
| `Noop` | `AbstractAction` | Final action that does nothing and passes |
| `Container` | `AbstractAction` | Final action for ordered composition with optional setup, teardown, and policy |
| `Parallel` | `AbstractAction` | Final action for concurrent running |

Runnable and composition actions expose one-shot builders that require a name up front and validate method arguments immediately:

```java
Direct.builder(String name).runnable(Direct.ThrowableRunnable runnable).build();
Container.builder(String name).before(Action before).child(Action child).after(Action after).build();
Parallel.builder(String name).parallelism(int parallelism).scheduler(AsyncScheduler scheduler).child(Action child).build();
```

`Noop.of(...)` remains the compact factory for no-op actions.
