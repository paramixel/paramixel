---
title: Actions
description: The fundamental execution unit in Paramixel.
---

# Actions

An `Action` is the fundamental execution unit in Paramixel. Tests are composed as trees of reusable `Action` definitions that the `Runner` discovers into descriptor occurrences and executes.

## The Action interface

```java
package org.paramixel.api.action;

import org.paramixel.spi.action.ExecutionContext;

public interface Action<T> extends Spec<T> {
    String name();
    String kind();
    default Optional<Action<?>> before() { ... }
    default List<Action<?>> children() { ... }
    default Optional<Action<?>> after() { ... }
    void execute(ExecutionContext context);
}
```

Every action:

- Has a display name used in console output and reports.
- Has a kind used to identify the action type in console output and reports.
- Executes one descriptor occurrence through an `ExecutionContext`.
- Sets its descriptor status to `RUNNING` and then to a terminal `Status`.
- Fires before/after execution listener callbacks for its own execution boundary.

## Discovery and execution

Paramixel separates tree construction from execution:

1. **Discovery** builds a descriptor tree from the action tree defined by `Spec` composition.
2. **Execution** calls `execute(ExecutionContext)` for each descriptor occurrence.
3. Built-in composite actions expose their child structure via `before()`, `children()`, and `after()`, and handle child scheduling internally through the descriptor tree.
4. Execution state lives in `Metadata`, available from `Descriptor#metadata()`.

## Action types

| Type | Package | Description |
| --- | --- | --- |
| [`Step`](./elements#step) | `org.paramixel.api.action` | Single callback; the leaf action |
| [`Lifecycle`](./elements#lifecycle) | `org.paramixel.api.action` | Before/body/after composition |
| [`Sequential`](./elements#sequential) | `org.paramixel.api.action` | Ordered dependent or independent children |
| [`Parallel`](./elements#parallel) | `org.paramixel.api.action` | Concurrent children with a per-action parallelism limit |
| [`Instance`](./elements#instance) | `org.paramixel.api.action` | Factory-created instance with automatic lifecycle |
| [`Static`](./elements#static) | `org.paramixel.api.action` | Instance-free before/body/after lifecycle |
| [`Delay`](./elements#delay) | `org.paramixel.api.action` | Fixed or random duration pause |
| [`Repeat`](./elements#repeat) | `org.paramixel.api.action` | Repeated execution of a single child |
| [`Timeout`](./elements#timeout) | `org.paramixel.api.action` | Wall-clock deadline for a single child |
| [`AssertTrue`](./elements#asserttrue) | `org.paramixel.api.action` | Asserts a boolean condition is true |
| [`AssertFalse`](./elements#assertfalse) | `org.paramixel.api.action` | Asserts a boolean condition is false |

## Child execution

Built-in composite actions handle child scheduling internally through the descriptor tree. Composite actions define their child structure via `Spec` composition, and the built-in composites walk the descriptor tree to execute children. Custom actions should compose with built-in composites for any child execution rather than attempting to drive child descriptors directly.

## Action modes

Each descriptor execution has a `Mode` in its metadata:

| Mode | Behavior |
| --- | --- |
| `RUN` | Execute the action's logic |
| `SKIP` | Produce a skipped outcome without running the action body |
| `ABORT` | Produce an aborted outcome without running the action body |

Built-in actions check the mode before executing user callbacks. Custom actions should do the same.

## Minimal custom action

```java
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;

public final class NoOpAction implements Action<Void> {
    @Override
    public String name() {
        return "no-op";
    }

    @Override
    public String kind() {
        return "NoOpAction";
    }

    @Override
    public void execute(ExecutionContext context) {
        var descriptor = context.descriptor();
        var listener = context.listener();
        listener.onBeforeExecution(descriptor);
        context.setStatus(Status.RUNNING);
        try {
            var mode = descriptor.metadata().mode();
            if (mode != Mode.RUN) {
                context.setStatus(mode.toStatus());
            } else {
                context.setStatus(Status.PASSED);
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(t));
        } finally {
            listener.onAfterExecution(descriptor);
        }
    }
}
```

See [Custom Actions](../guides/custom-actions) for the full custom action contract.

## Reusability

Framework actions are immutable and reusable across concurrent runs. If a custom action has mutable fields, it must provide its own synchronization. Prefer local variables, descriptor metadata, or `Instance`-managed fixture objects for per-execution state.
