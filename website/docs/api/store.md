---
title: Store & Value
description: Key-value state storage on Context.
---

# Store & Value

`Store` and `Value` replace the old single-attachment model. Each `Context` has its own independent `Store`.

## Store

`Store` is a thread-safe key-value map using `String` keys and `Value` values.

### Writing

```java
context.getStore().put("key", Value.of(myObject));
```

### Reading

```java
// From current context
context.getStore().get("key");

// From ancestor context
context.findAncestor(1).getStore().get("key");
```

### Type-safe access

```java
MyData data = context.getStore()
        .get("key")
        .map(Value::get)
        .map(v -> (MyData) v)
        .orElseThrow();

// Or use type-check and cast
Value value = context.getStore().get("key").orElseThrow();
if (value.isType(MyData.class)) {
    MyData data = value.cast(MyData.class);
}
```

### Removing

```java
context.getStore().remove("key");
context.getStore().clear();
```

### Full API

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

## Value

`Value` wraps any object:

```java
Value.of(anyObject)          // factory, rejects null
value.get()                   // returns the wrapped object
value.isType(MyClass.class)   // type-check without casting
value.cast(MyClass.class)     // typed cast, throws ClassCastException if type doesn't match
```

## Ancestor access pattern

To read data stored by an ancestor:

```java
// Before (1.x):
context.findAttachment(1).flatMap(a -> a.to(MyData.class))

// After (2.0.0):
context.findAncestor(1).getStore().get("key").map(Value::get).map(v -> (MyData) v)
```

Each context has its own independent `Store`. Use `findAncestor(levelUp)` to navigate to an ancestor context, then call `getStore()` on it.