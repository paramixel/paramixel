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
| `initialize(Configuration configuration)` | Invoked once before the run begins; allows listeners to configure themselves |
| `onRunStarted()` | Invoked once before run discovery begins |
| `onDiscoveryStarted()` | Invoked after `onRunStarted()`, before the discovery phase |
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
| Report listener (optional) | Writes a report file when `paramixel.report.file` is configured; format determined by file extension |

The report listener is selected based on the [`paramixel.report.file`](../configuration/properties#paramixelreportfile) extension:

| Extension | Listener | Format |
| --- | --- | --- |
| `.json` | `JsonReportListener` | JSON with tree hierarchy |
| `.xml` | `XmlReportListener` | XML with tree hierarchy |
| `.html` / `.htm` | `HtmlReportListener` | Self-contained HTML |
| Other | `ReportListener` | Plain text tree summary |

When `paramixel.report.file` is unset, the chain includes only `StatusListener` and `SummaryListener`. The chain is wrapped in `SafeListener` to prevent listener exceptions from affecting the run.

`StatusListener` and `SummaryListener` respect the [`paramixel.listener.exclude`](../configuration/properties#paramixellistenerexclude) configuration key, which controls which output sections are suppressed.

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
initialize(configuration)
  onRunStarted()
  onDiscoveryStarted()
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
