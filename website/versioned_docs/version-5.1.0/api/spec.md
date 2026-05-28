---
title: Spec
description: The accumulating specification interface for building Action instances.
---

# Spec

`org.paramixel.api.action.Spec<T>` is the interface for building `Action` instances. Accumulating specs collect configuration (children, before/after, parallelism, etc.) and produce an immutable `Action` on `resolve()`.

```java
import org.paramixel.api.action.Spec;
```

## Interface

```java
public interface Spec<T> {
    Action<T> resolve();
}
```

`Action<T>` extends `Spec<T>`. When `resolve()` is called on an already-built `Action`, it returns itself. When called on an accumulating spec (such as `Lifecycle.Spec`, `Sequential.Spec`, or `Parallel.Spec`), it builds the immutable action from accumulated configuration.

## Accumulating specs

Each composite action type provides an accumulating `Spec` inner class:

| Spec | Factory | Purpose |
| --- | --- | --- |
| `Lifecycle.Spec<T>` | `Lifecycle.of(name)` | Accumulates before, children, after, and dependency mode |
| `Sequential.Spec<T>` | `Sequential.of(name)` | Accumulates children and dependency mode |
| `Parallel.Spec<T>` | `Parallel.of(name)` | Accumulates children and parallelism |
| `Instance.Spec<T>` | `Instance.of(name, supplier)`, `Instance.of(Class)`, `Instance.of(name, Class)` | Accumulates children and dependency mode |
| `Static.Spec` | `Static.of(name)` | Accumulates before, children, after, and dependency mode |
| `Repeat.Spec<T>` | `Repeat.of(name)` | Accumulates a single child and repeat count |
| `Timeout.Spec` | `Timeout.of(name)` | Accumulates a single child and timeout duration |

Accumulating specs are **single-use** — calling `resolve()` produces the action and the spec should not be reused.

## Usage pattern

```java
Lifecycle.of("suite")
        .before(Step.of("setup", ctx -> { /* setup */ }))
        .child(Step.of("test-1", ctx -> { /* test */ }))
        .child(Step.of("test-2", ctx -> { /* test */ }))
        .after(Step.of("teardown", ctx -> { /* cleanup */ }))
        .resolve();
```

The `of(name)` factory returns the accumulating spec. Each method call (`before`, `child`, `after`) mutates the spec and returns it for chaining. `resolve()` produces the immutable `Lifecycle` action.

## Dynamic composition

Specs support dynamic composition with loops or the `each()` convenience method:

### For-loop

```java
var spec = Parallel.of("suite")
        .parallelism(4);
for (TestCase tc : testCases()) {
    spec.child(argument(tc));
}
return spec.resolve();
```

### Each convenience method

`Lifecycle.Spec`, `Sequential.Spec`, `Parallel.Spec`, `Instance.Spec`, and `Static.Spec` provide `each(Iterable, Function)` as a convenience method that maps a collection to child actions, producing an identical tree:

```java
return Parallel.of("suite")
        .parallelism(4)
        .each(testCases(), tc -> argument(tc))
        .resolve();
```

The mapper is called at spec-building time. An empty iterable adds no children and resolves cleanly.

## Leaf actions

Leaf actions (`Step`, `Delay`, `AssertTrue`, `AssertFalse`) implement `Action<T>` directly. They return themselves from `resolve()` and do not have accumulating specs.

```java
Step.of("name", consumer);  // returns Action directly, no resolve() needed
```

## Spec in the Action interface

`Action<T>` extends `Spec<T>`, so any `Action` can be used wherever a `Spec` is expected:

```java
Lifecycle.of("suite")
        .before(Step.of("setup", ctx -> {}))  // Step is both Action and Spec
        .child(Sequential.of("sub-suite")      // Spec returned by of()
                .child(test1)
                .child(test2)
                .resolve())                    // resolve() before passing
        .resolve();
```

Methods that accept `Spec<?>` can receive either a pre-built `Action` or an accumulating spec that hasn't been resolved yet. However, the canonical pattern is to call `resolve()` before passing to ensure the action is fully constructed.

## See also

- [Action](./action)
- [Elements](../core-concepts/elements)
