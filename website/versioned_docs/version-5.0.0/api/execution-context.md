---
title: ExecutionContext and Mode
description: SPI runtime services for action execution and mode-based execution control.
---

# ExecutionContext and Mode

The `ExecutionContext` SPI interface provides runtime services for an active descriptor execution. `Mode` determines how an action executes.

```java
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;
```

## ExecutionContext

```java
public interface ExecutionContext {
    Configuration configuration();
    Listener listener();
    Descriptor descriptor();
    <T> Optional<T> instance(Class<T> type);
    void setStatus(Status status);
}
```

| Method | Description |
| --- | --- |
| `configuration()` | The run configuration |
| `listener()` | The listener for callbacks |
| `descriptor()` | The descriptor currently being executed |
| `instance(Class<T> type)` | The current fixture instance from a parent `Instance` action, or empty |
| `setStatus(Status)` | Sets the descriptor status; must be `RUNNING` or terminal |

### Status transitions

`setStatus()` validates transitions:
- `PENDING` → `RUNNING` is valid
- `RUNNING` → terminal (`PASSED`, `FAILED`, `SKIPPED`, `ABORTED`) is valid
- `PENDING` as a target status throws `IllegalArgumentException`
- Transitions from a terminal status throw `IllegalStateException`

### Setting status with detail

To set a terminal status with a message or throwable, use `Status` factory methods:

```java
context.setStatus(Status.failed("connection refused"));
context.setStatus(Status.failed("timeout", new TimeoutException()));
```

### Using instance

Within an `Instance` action, `instance()` returns the fixture object created by the instance factory. Outside an `Instance` scope, returns `Optional.empty()`.

```java
@Override
public void execute(ExecutionContext context) {
    Optional<MyService> service = context.instance(MyService.class);
    service.ifPresent(s -> s.performWork());
}
```

## Mode

```java
public enum Mode {
    RUN,
    SKIP,
    ABORT;
}
```

| Value | Behavior |
| --- | --- |
| `RUN` | Execute the action's logic normally |
| `SKIP` | Produce a skipped outcome without running the action body |
| `ABORT` | Produce an aborted outcome without running the action body |

Modes are per-execution, not properties of reusable `Action` definitions.

| Method | Description |
| --- | --- |
| `toStatus()` | Returns the terminal status for this mode |
| `fromStatus(Status)` | Returns the mode to propagate based on a terminal status |

### Mode-to-Status mapping

| Mode | `toStatus()` |
| --- | --- |
| `RUN` | `PASSED` |
| `SKIP` | `SKIPPED` |
| `ABORT` | `ABORTED` |

### Status-to-Mode propagation

| Terminal status | `fromStatus()` |
| --- | --- |
| `ABORTED` | `ABORT` |
| Any other terminal | `SKIP` |

Built-in actions check the mode before executing user callbacks. Custom actions should do the same:

```java
@Override
public void execute(ExecutionContext context) {
    var mode = context.descriptor().metadata().mode();
    if (mode != Mode.RUN) {
        context.setStatus(mode.toStatus());
        return;
    }
    // ... execute action logic ...
    context.setStatus(Status.PASSED);
}
```

See [Custom Actions](../guides/custom-actions) for the full custom action contract and [Action](./action) for the action interface.
