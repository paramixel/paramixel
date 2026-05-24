---
title: Custom Actions
description: Implementing custom Paramixel action semantics.
---

# Custom Actions

Custom actions are the extension point for new execution semantics. Implement `Action<T>` directly when built-in actions such as `Step`, `Lifecycle`, `Sequential`, and `Parallel` do not express the behavior you need.

:::tip

For most use cases, prefer composing built-in actions rather than implementing `Action` directly. Custom actions require careful handling of listener callbacks, status transitions, and mode propagation. Built-in composite actions handle child execution internally; custom composite actions that need to execute children must compose with built-in actions rather than attempting to drive child descriptors directly.

:::

## SPI overview

Custom actions use these types:

| Type | Package | Purpose |
| --- | --- | --- |
| `Action<T>` | `org.paramixel.api.action` | reusable action definition |
| `Descriptor` | `org.paramixel.api.action` | active node in the execution tree |
| `Status` | `org.paramixel.api` | terminal and intermediate statuses |
| `ExecutionContext` | `org.paramixel.spi.action` | accesses configuration, listener, active descriptor, and status setters |
| `Mode` | `org.paramixel.spi.action` | `RUN`, `SKIP`, or `ABORT` |

## Contract

A custom action should:

1. Return a nonblank display name from `name()`.
2. Return a nonblank kind from `kind()`.
3. In `execute(ExecutionContext)`, set its own status and fire listener callbacks.
4. Respect `Mode.SKIP` and `Mode.ABORT` — propagate non-run modes to children.

## Complete example: custom leaf action

Custom **leaf** actions implement `Action<T>` directly for their own execution logic. The framework handles child scheduling for built-in composites; custom composite semantics should be achieved by composing built-in actions rather than implementing child execution from scratch.

```java
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;

public final class RetryStep implements Action<Void> {
    private final String name;
    private final int maxAttempts;
    private final Runnable operation;

    public RetryStep(String name, int maxAttempts, Runnable operation) {
        this.name = name;
        this.maxAttempts = maxAttempts;
        this.operation = operation;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String kind() {
        return "RetryStep";
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
                Throwable lastException = null;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    try {
                        operation.run();
                        context.setStatus(Status.PASSED);
                        return;
                    } catch (Throwable t) {
                        lastException = t;
                    }
                }
                context.setStatus(Status.failed("all " + maxAttempts + " attempts failed", lastException));
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(t));
        } finally {
            listener.onAfterExecution(descriptor);
        }
    }

    @Override
    public Action<Void> resolve() {
        return this;
    }
}
```

### Composing for custom composite semantics

When you need custom composite behavior (e.g. a repeating action that drives children), compose with built-in actions:

```java
public static Action<?> repeated(String name, Action<?> child, int count) {
    return Repeat.of(name)
            .count(count)
            .child(child)
            .resolve();
}
```

The framework's built-in composites (`Repeat`, `Sequential`, `Parallel`, `Lifecycle`, `Instance`, `Static`) handle all child descriptor creation, scheduling, and status aggregation internally. Custom actions should implement only their own execution logic and delegate composite behavior to these built-in types.

## Execution lifecycle

Every custom action's `execute()` method must follow this lifecycle:

1. Obtain the `Descriptor` and `Listener` from the context.
2. Call `listener.onBeforeExecution(descriptor)` at the start.
3. Set status to `RUNNING` via `context.setStatus(Status.RUNNING)`.
4. Perform the action's work (propagate mode, aggregate child statuses, etc.).
5. Set a terminal status (`PASSED`, `FAILED`, `SKIPPED`, or `ABORTED`).
6. Call `listener.onAfterExecution(descriptor)` in a `finally` block.

Wrap the work in a try-catch to map exceptions to status via `Status.fromThrowable(t)`. Place `listener.onAfterExecution(descriptor)` in a `finally` block to ensure it is always called.

### Setting terminal status with detail

When a custom action needs to set a terminal status with a message or throwable, use the named factory methods:

```java
context.setStatus(Status.failed("custom error message"));
context.setStatus(Status.aborted("precondition not met", exception));
```

Construct the full `Status` using factory methods before calling `setStatus`.

## Status aggregation

Use `Status.aggregate(children)` for the built-in severity order:

`FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`.

## Thread safety

Actions are reusable definitions. Do not store per-execution state in mutable action fields unless you also provide synchronization. Prefer local variables inside `execute(...)`, descriptor metadata, or `Instance`-managed fixture objects.

## See also

- [Action API](../api/action)
- [ExecutionContext and Mode](../api/execution-context)
- [Elements](../core-concepts/elements)
