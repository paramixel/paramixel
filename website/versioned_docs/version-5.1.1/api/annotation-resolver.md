---
title: AnnotationResolver
description: Resolves @Paramixel.Id annotated methods into named Action instances.
---

# AnnotationResolver

`AnnotationResolver` resolves `@Paramixel.Id` annotated methods on a concrete type into named `Action` instances. It is the bridge between annotation-driven test methods and the action tree.

```java
import org.paramixel.api.AnnotationResolver;
```

## Creating a resolver

```java
AnnotationResolver<MyTest> resolver = AnnotationResolver.create(MyTest.class);
```

## Resolving instance methods

`byId(String id)` resolves an instance method annotated with `@Paramixel.Id` and returns an `Action<T>` that invokes it:

```java
@Paramixel.Id("before")
public void before() { /* setup */ }

@Paramixel.Id("test")
public void test() { /* test logic */ }

@Paramixel.Id("after")
public void after() { /* cleanup */ }
```

```java
AnnotationResolver<MyTest> resolver = AnnotationResolver.create(MyTest.class);

return Instance.of(MyTest.class)
    .child(Lifecycle.of("lifecycle")
        .before(resolver.byId("before"))
        .child(resolver.byId("test"))
        .after(resolver.byId("after"))
        .resolve())
    .resolve();
```

**`byId(String id, String kind)`** — Same as `byId(id)` but with a custom kind on the returned action.

## Resolving static methods

`staticById(String id)` resolves a static method annotated with `@Paramixel.Id` and returns an `Action<?>`:

```java
@Paramixel.Id("staticSetUp")
public static void staticSetUp() { /* static setup */ }
```

```java
AnnotationResolver<MyTest> resolver = AnnotationResolver.create(MyTest.class);

return Static.of("MyTest")
    .before(resolver.staticById("staticSetUp"))
    .child("test()", () -> { /* ... */ })
    .resolve();
```

**`staticById(String id, String kind)`** — Same as `staticById(id)` but with a custom kind on the returned action.

## Method signature requirements

Annotated methods must be:
- Instance methods for `byId()`, static methods for `staticById()`
- No-argument (`void` return type)
- Only one method per identifier per class

Duplicate identifiers throw `IllegalArgumentException`. Invalid signatures throw `IllegalArgumentException`.

## Caching

Resolved methods are discovered and cached per class in thread-safe LRU caches (capacity 100, 60-second TTL). Instance and static methods are cached independently.

| Method | Description |
| --- | --- |
| `clearCache(Class<?>)` | Invalidates the resolver cache for the given type |
| `clearAllCache()` | Invalidates all resolver cache entries |

Cache is populated on first `byId()` or `staticById()` call for a given type and reused for subsequent calls.
