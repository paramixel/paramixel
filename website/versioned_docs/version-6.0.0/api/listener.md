---
title: Listener
description: Callback interface for discovery and execution events.
---

# Listener

`Listener` receives lifecycle callbacks during discovery and execution.

## Callbacks

- `initialize(Configuration)`
- `onDiscoveryStarted()`
- `onDiscoveryCompleted(Descriptor root)`
- `onRunStarted()`
- `onBeforeExecution(Descriptor descriptor)`
- `onAfterExecution(Descriptor descriptor)`
- `onRunCompleted(Result result)`

All methods have default no-op implementations, so custom listeners can override only what they need.

```java
Listener listener = new Listener() {
    @Override
    public void onRunCompleted(Result result) {
        System.out.println(result.isPassed() ? "PASSED" : "NOT PASSED");
    }
};

var runner = Runner.builder().listener(listener).build();
```

`Listener.defaultListener(configuration)` creates the framework's console/report listener chain.
