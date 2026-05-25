---
title: Listener
description: Callback interface for run, discovery, and descriptor execution lifecycle events.
---

# Listener

The `Listener` interface receives run-level and descriptor execution callbacks. Actions invoke `onBeforeExecution(Descriptor)` and `onAfterExecution(Descriptor)` around their own execution boundary. The runner invokes run-level and discovery callbacks.

```java
import org.paramixel.api.Listener;
```

## Callbacks

| Method | Description |
| --- | --- |
| `onRunStarted()` | Invoked once before run discovery begins |
| `onDiscoveryCompleted(Descriptor root)` | Invoked after discovery creates the descriptor tree |
| `onBeforeExecution(Descriptor descriptor)` | Invoked by an action immediately before its execution logic runs |
| `onAfterExecution(Descriptor descriptor)` | Invoked by an action after its descriptor reaches a terminal status |
| `onRunCompleted(Result result)` | Invoked once after the run completes |

All methods have default no-op implementations. Implement only the callbacks you need.

## Default listener chain

```java
Listener listener = Listener.defaultListener();
Listener listener = Listener.defaultListener(configuration);
```

The default listener chain includes:

| Component | Description |
| --- | --- |
| `StatusListener` | Prints status updates to the console with ANSI support |
| `SummaryListener` | Prints a tree summary of the run result |
| `ReportListener` | Writes a report file when `paramixel.report.file` is configured |

The chain is wrapped in `SafeListener` to prevent listener exceptions from affecting the run.

## Custom listener

```java
Listener customListener = new Listener() {
    @Override
    public void onRunStarted() {
        System.out.println("Run started");
    }

    @Override
    public void onDiscoveryCompleted(Descriptor root) {
        System.out.println("Discovered " + root.children().size() + " root actions");
    }

    @Override
    public void onAfterExecution(Descriptor descriptor) {
        var meta = descriptor.metadata();
        if (!meta.status().isPassed()) {
            System.out.println("  FAILED: " + meta.name());
        }
    }

    @Override
    public void onRunCompleted(Result result) {
        System.out.println("Run completed: " + result.status().name());
    }
};

Runner runner = Runner.builder()
    .listener(customListener)
    .build();
```

## Callback lifecycle

The callbacks occur in this order:

```
onRunStarted()
  onDiscoveryCompleted(root)
  [for each descriptor execution:]
    onBeforeExecution(descriptor)
    onAfterExecution(descriptor)
onRunCompleted(result)
```

## See also

- [Result](./result)
- [Descriptor and Metadata](./descriptor-and-metadata)
- [Reporting](../integrations/reporting)
