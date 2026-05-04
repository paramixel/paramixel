---
title: Context
description: Runtime state, hierarchy, and store.
---

# Context

Every action receives a `Context` during execution.

## Methods

```java
Optional<Context> getParent()
Map<String, String> getConfiguration()
Listener getListener()
ExecutorService getExecutorService()
Store getStore()
Optional<Context> findAncestor(int levelUp)
```

## Hierarchy

Contexts form a parent/child chain that mirrors nested execution.

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

From `examples/test/context/ContextHierarchyTest.java`, a `before` action stores data and descendants read it later:

```java
Action action = Lifecycle.of(
        "test",
        Direct.of("before", context -> {
            context.getStore().put("data", Value.of("suite-value"));
        }),
        Direct.of("main", context -> {
            String value = context.findAncestor(1)
                    .orElseThrow()
                    .getStore()
                    .get("data")
                    .map(Value::get)
                    .map(v -> (String) v)
                    .orElseThrow();
        }),
        Noop.of("after"));
```

## Configuration access

Prefer reading runtime settings from `context.getConfiguration()` inside actions rather than reaching out to JVM properties directly.