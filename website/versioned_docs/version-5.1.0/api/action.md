---
title: Action
description: The fundamental execution unit.
---

# Action

`org.paramixel.api.action.Action<T>` is the reusable execution definition processed by a `Runner`.
Discovery binds each action occurrence to a descriptor; execution mutates descriptor metadata through a `Context`.

## Interface

```java
package org.paramixel.api.action;

import java.util.List;
import java.util.Optional;
import org.paramixel.api.action.Context;

public interface Action<T> extends Spec<T> {
    String name();
    String kind();
    default Optional<Action<?>> before() { return Optional.empty(); }
    default List<Action<?>> children() { return List.of(); }
    default Optional<Action<?>> after() { return Optional.empty(); }
    void execute(Context context);

    @Override
    default Action<T> resolve() {
        return this;
    }
}
```

## Type parameter

The type parameter `T` represents the type consumed by action callbacks. For `Instance<T>` child steps, `T` is the managed instance type. For a `Step` outside an instance, the callback receives the current `Context`.

## Key methods

### `name()`

Returns the human-readable display name used in console output and reports. Names must not be `null` or blank.

### `kind()`

Returns the kind name for this action, used in console output and reports. Built-in actions return their simple name (e.g. `"Step"`, `"Lifecycle"`). Custom actions must implement this method to declare their own kind. The kind appears in parentheses after the action display name in console output.

### `execute(Context)`

Executes this action occurrence. Implementations are responsible for their own status transitions and listener callbacks. Built-in composite actions handle child descriptor scheduling internally; custom actions should compose with built-in composites rather than attempting to drive child execution directly.

```java
@Override
public void execute(Context context) {
    var mode = context.descriptor().metadata().mode();
    if (mode != Mode.RUN) {
        context.setStatus(mode.toStatus());
        return;
    }
    // action body
    context.setStatus(Status.PASSED);
}
```

## Child actions

Child actions are declared via `before()`, `children()`, and `after()`. Built-in composite actions handle child descriptor scheduling and execution internally. Custom actions that need composite behavior should compose with built-in actions rather than implementing child execution directly.

## Spec

`Action` extends `Spec<T>`. Leaf action definitions return themselves from `resolve()`. Accumulating specs such as `Lifecycle.Spec`, `Sequential.Spec`, and `Parallel.Spec` create immutable action instances from configured children.

## Reusability

Actions are reusable definitions. The same `Action` instance can appear in multiple places in the tree; each occurrence receives a separate descriptor with independent `Metadata`. Avoid mutable per-execution state in action fields. Use `Instance` for managed fixture objects or keep state local to execution.
