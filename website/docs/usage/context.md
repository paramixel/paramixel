---
title: Context
description: Runtime state, hierarchy, and store.
---

# Context

Every action receives a `Context` during execution.

## Action Context Mode

Each action owns its context scoping policy:

```java
Action.ContextMode.ISOLATED
Action.ContextMode.SHARED
```

`ISOLATED` means the action executes with `context.createChild()` when the action implementation honors the mode.

`SHARED` means the action executes with the same context it received from its parent when the action implementation honors the mode.

Built-in actions honor `ContextMode`. Custom action implementations are responsible for applying their own context scoping in `execute(Context)` and `skip(Context)`. Parent actions pass their effective context directly to children; children decide whether to isolate or share.

Use shared mode when sibling actions intentionally share workflow state:

```java
private static Action setup() {
    return Direct.builder("setup")
            .contextMode(Action.ContextMode.SHARED)
            .execute(context -> context.getStore().put("token", Value.of("abc")))
            .build();
}
```

### SHARED vs ISOLATED

With `ISOLATED` (default), each action gets its own child context with an independent store. Sibling actions cannot see each other's store data directly.

With `SHARED`, the action reuses the context it received from its parent. This means lifecycle phases (`before`, body children, `after`) all operate on the same store:

```java
// All three actions use SHARED to read/write the same store
Action before = Direct.builder("before")
        .contextMode(Action.ContextMode.SHARED)
        .execute(context -> context.getStore().put("shared-key", Value.of("hello")))
        .build();

Action writeChild = Direct.builder("write-child")
        .contextMode(Action.ContextMode.SHARED)
        .execute(context -> context.getStore().put("child-key", Value.of("world")))
        .build();

Action readChild = Direct.builder("read-child")
        .contextMode(Action.ContextMode.SHARED)
        .execute(context -> {
            // Both keys are visible because all actions share the same context
            String shared = context.getStore().get("shared-key").orElseThrow().cast(String.class);
            String child = context.getStore().get("child-key").orElseThrow().cast(String.class);
        })
        .build();
```

### Ancestor navigation for isolated contexts

When actions use `ISOLATED` mode, use `findAncestor()` to navigate up the context chain and access data from an ancestor's store:

```java
// A deeply nested action reads data stored by a SHARED before action
// at the argument-container level (2 levels up)
String value = context.findAncestor(2)
        .orElseThrow()
        .getStore()
        .get("arg-key")
        .orElseThrow()
        .cast(String.class);
```

## Methods

```java
Optional<Context> getParent()
Map<String, String> getConfiguration()
Listener getListener()
CompletableFuture<Result> runAsync(Action action)
Store getStore()
Optional<Context> findAncestor(int levelUp)
Context createChild()
```

`runAsync(action)` schedules an action through the effective scheduler for the current context. Inside a `Parallel` subtree configured with `scheduler(...)`, nested `context.runAsync(...)` calls use that same custom scheduler.

## Hierarchy

Contexts form a parent/child chain based on action context modes. With `ISOLATED`, an action creates a child context. With `SHARED`, it reuses the received context.

- `getParent()` returns the immediate parent context
- `findAncestor(0)` returns the current context
- `findAncestor(1)` returns the parent
- larger levels walk farther up the chain
- `findAncestor(levelUp)` throws `IllegalArgumentException` for negative levels
- `findAncestor(levelUp)` returns `Optional.empty()` when that ancestor does not exist

## Store

Each context owns an independent `Store` — a thread-safe key-value map using `String` keys and `Value` values.

### Writing values

```java
context.getStore().put("key", Value.of(someObject));
```

### Reading values

```java
// From the current context
context.getStore().get("key");

// From an ancestor context
context.findAncestor(1).orElseThrow().getStore().get("key");
```

### Store API

`Store` provides a `ConcurrentMap`-like interface:

```java
Optional<Value> get(String key)
Optional<Value> put(String key, Value value)
Optional<Value> remove(String key)
Optional<Value> putIfAbsent(String key, Value value)
boolean containsKey(String key)
int size()
boolean isEmpty()
void clear()
void forEach(BiConsumer<? super String, ? super Value> action)
Optional<Value> computeIfAbsent(String key, Function<? super String, ? extends Value> mappingFunction)
Optional<Value> computeIfPresent(String key, BiFunction<? super String, ? super Value, ? extends Value> remappingFunction)
Optional<Value> compute(String key, BiFunction<? super String, ? super Value, ? extends Value> remappingFunction)
Optional<Value> merge(String key, Value value, BiFunction<? super Value, ? super Value, ? extends Value> remappingFunction)
// ... and more
```

Every method that returns a store value returns `Optional<Value>`. All methods reject `null` keys and values with `NullPointerException`.

### Value

`Value` wraps any object:

```java
Value.of(anyObject)          // factory, rejects null
value.get()                  // returns the wrapped object
value.isType(MyClass.class)  // type-check without casting
value.cast(MyClass.class)    // typed cast
```

## Example pattern

This pattern mirrors the context examples under `examples/src/main/java/examples/context/`: a `before` action stores data and descendants read it later.

```java
public class SharedContextPattern {

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
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> context.getStore().put("data", Value.of("suite-value")))
                .build();
    }

    private static Action main() {
        return Direct.builder("main")
                .execute(context -> {
                    String value = context.findAncestor(1)
                            .orElseThrow()
                            .getStore()
                            .get("data")
                            .orElseThrow()
                            .cast(String.class);
                })
                .build();
    }

    private static Action after() {
        return Noop.of("after");
    }
}
```

## Configuration access

Prefer reading runtime settings from `context.getConfiguration()` inside actions rather than reaching out to JVM properties directly.
