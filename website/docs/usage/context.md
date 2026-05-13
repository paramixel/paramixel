---
title: Context
description: Runtime state, hierarchy, and store.
---

# Context

Every action receives a `Context` during execution.

## Context scoping

Context scoping is owned by composite actions. Built-in actions apply the following rules:

- **`Container`** shares its received context with `before` and `after` actions, and creates an isolated child context for each body child.
- **`Parallel`** creates an isolated child context for each child.
- **`Direct`** uses whatever context it receives from its parent.

Custom action implementations are responsible for applying their own context scoping in `run(Context)` and `skip(Context)`.

### Sharing state between before and after

`Container` shares its context with `before` and `after` actions, so they can read and write the same store directly:

```java
private static Action setup() {
    return Direct.builder("setup")
            .runnable(context -> context.getStore().put("token", "abc"))
            .build();
}
```

### Accessing parent state from body children

Body children in a `Container` receive isolated child contexts. Use `getAncestor("../")` to navigate up and access data stored by `before`:

```java
// A body child reads data stored by the before action (1 level up)
String value = context.getAncestor("../")
        .getStore()
        .get("data", String.class)
        .orElseThrow();
```

### Sharing state between parallel children

`Parallel` creates an isolated child context for each child. To share state, store data on the parent context (e.g., in a `Container.before`) and access it from each child via `getAncestor("../")`:

```java
Action before = Direct.builder("before")
        .runnable(context -> context.getStore().put("shared-key", "hello"))
        .build();

Action child = Direct.builder("child")
        .runnable(context -> {
            String shared = context.getAncestor("../")
                    .getStore()
                    .get("shared-key", String.class)
                    .orElseThrow();
        })
        .build();
```

## Methods

```java
Context getParent()
Optional<Context> findParent()
Context getAncestor(String path)
Optional<Context> findAncestor(String path)
Map<String, String> getConfiguration()
Listener getListener()
CompletableFuture<Result> runAsync(Action action)
Store getStore()
Context createChild()
```

`runAsync(action)` schedules an action through the effective scheduler for the current context. Inside a `Parallel` subtree configured with `scheduler(...)`, nested `context.runAsync(...)` calls use that same custom scheduler.

## Hierarchy

Contexts form a parent/child chain based on composite action scoping. `Container` shares context with `before`/`after` but creates child contexts for body children. `Parallel` creates a child context for each child.

- `getParent()` returns the immediate parent context, throws `AncestorNotFoundException` at root
- `findParent()` returns the immediate parent context wrapped in `Optional` (equivalent to `findAncestor("../")`)
- `findAncestor("../")` returns the parent
- `findAncestor("../../")` returns the grandparent
- `findAncestor("/")` returns the root context
- Both `"../"` and `".."` forms are accepted
- `findAncestor(path)` returns `Optional.empty()` when the path traverses beyond the root
- `findAncestor(path)` throws `IllegalArgumentException` for named segments or `.` segments
- `getAncestor(path)` returns the ancestor context directly, throws `AncestorNotFoundException` when path traverses beyond root

## Store

Each context owns an independent `Store` — a thread-safe key-value map using `String` keys.

### Writing values

```java
context.getStore().put("key", someObject);
```

### Reading values

```java
// From the current context
context.getStore().get("key");

// From an ancestor context
context.getAncestor("../").getStore().get("key");
```

### Store API

`Store` provides a `ConcurrentMap`-like interface:

```java
Optional<Object> get(String key)
Optional<Object> put(String key, Object value)
Optional<Object> remove(String key)
Optional<Object> putIfAbsent(String key, Object value)
boolean containsKey(String key)
int size()
boolean isEmpty()
void clear()
void forEach(BiConsumer<? super String, ? super Object> action)
Optional<Object> computeIfAbsent(String key, Function<? super String, ? extends Object> mappingFunction)
Optional<Object> computeIfPresent(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction)
Optional<Object> compute(String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction)
Optional<Object> merge(String key, Object value, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction)
<T> Optional<T> get(String key, Class<T> type)
<T> Optional<T> remove(String key, Class<T> type)
<T> T getOrDefault(String key, Class<T> type, T defaultValue)
boolean isType(String key, Class<?> type)
// ... and more
```

Every method that returns a store value returns `Optional<Object>`. Use `get(key, type)` for typed access. All methods reject `null` keys and values with `NullPointerException`.

## Example pattern

This pattern mirrors the context examples under `examples/src/main/java/examples/lifecycle/`: a `before` action stores data and descendants read it later.

```java
public class SharedContextPattern {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action before = before();
        Action main = main();
        Action after = after();

        return Container.builder("test")
                .before(before)
                .child(main)
                .after(after)
                .build();
    }

    private static Action before() {
        return Direct.builder("before")
                .runnable(context -> context.getStore().put("data", "suite-value"))
                .build();
    }

    private static Action main() {
        return Direct.builder("main")
                .runnable(context -> {
                    String value = context.getAncestor("../")
                            .getStore()
                            .get("data", String.class)
                            .orElseThrow();
                })
                .build();
    }

    private static Action after() {
        return Noop.of("after");
    }
}
```

## Full lifecycle example

The `FullLifecycleTest` example under `examples/src/main/java/examples/lifecycle/` demonstrates context usage across a deep action hierarchy (suite → arguments → tests):

- **Suite `before`** stores data in the Container's own context store (`context.getStore().put(...)`)
- **Suite `after`** reads from the same store (`context.getStore().get(...)`) and accesses the root context (`context.getAncestor("/")`) — because `before` and `after` share the Container's context
- **Argument `before`** stores per-argument data, and **argument `after`** removes it — same shared context
- **Test `before`** stores a marker that **test `after`** reads — same shared context
- **Test body** navigates the ancestor hierarchy to access data from parent levels:
  - `context.getParent()` and `context.findParent()` for direct parent access
  - `context.getAncestor("../../../")` to access the argument-level store (three levels up: test-body → test → arg-body → arg)

This pattern shows how deeply nested actions use `getAncestor(path)` to reach ancestor stores while before/after pairs share their Container's context directly.

## Configuration access

Prefer reading runtime settings from `context.getConfiguration()` inside actions rather than reaching out to JVM properties directly.
