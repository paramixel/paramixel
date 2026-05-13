---
title: Store
description: Key-value state storage on Context.
---

# Store

`Store` replaces the old single-attachment model. Each `Context` has its own independent `Store`.

## Store

`Store` is a thread-safe key-value map using `String` keys.

### Writing

```java
context.getStore().put("key", myObject);
```

### Reading

```java
// From current context
context.getStore().get("key");

// From ancestor context
context.getAncestor("../").getStore().get("key");
```

### Type-safe access

```java
MyData data = context.getStore()
        .get("key", MyData.class)
        .orElseThrow();

// Type-check without casting
if (context.getStore().isType("key", MyData.class)) {
    MyData data = context.getStore().get("key", MyData.class).orElseThrow();
}
```

### Removing

```java
context.getStore().remove("key");
context.getStore().clear();
```

### Typed convenience methods

```java
Optional<T> get(String key, Class<T> type)               // typed get, returns Optional<T>
Optional<T> remove(String key, Class<T> type)             // typed remove, returns Optional<T>
T getOrDefault(String key, Class<T> type, T defaultValue) // typed getOrDefault
boolean isType(String key, Class<?> type)                  // returns false if key absent
```

### Full API

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

## Ancestor access pattern

To read data stored by an ancestor:

```java
// Before (1.x):
context.findAttachment(1).flatMap(a -> a.to(MyData.class))

// After (2.0.0):
context.getAncestor("../").getStore().get("key", MyData.class)
```

Each context has its own independent `Store`. Use `getAncestor(path)` to navigate when the ancestor is expected to exist, or `findAncestor(path)` for safe navigation, then call `getStore()` on it.

## Advanced operations

### Atomic compute

```java
context.getStore().compute("counter", (key, value) ->
        (Integer) value + 1);
```

### Conditional put

```java
context.getStore().putIfAbsent("key", "default");
```

### Merge

```java
context.getStore().merge("key", newValue, (oldVal, newVal) -> newVal);
```

### Conditional remove and replace

```java
context.getStore().remove("key", expectedValue);  // returns boolean
context.getStore().replace("key", oldValue, newValue);  // returns boolean
context.getStore().replace("key", updated);  // returns Optional<Object>
```
