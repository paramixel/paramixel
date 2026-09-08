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

## Console Output

The default listener chain includes `StatusListener`, which prints an execution-start line
before each descriptor runs and a completion line after it finishes.

**Execution-start format:**

```
[PARAMIXEL] <id-path> | <display-name-path>
```

**Completion format:**

```
[PARAMIXEL] <status> | <id-path> | <display-name-path> <elapsed-ms>
```

**ID path / thread name correlation** — the `<id-path>` field is the descriptor ID path
(hyphen-joined chain of four-character descriptor IDs from root to leaf) and is also
the JVM thread name during execution. The scheduler assigns the thread name via
`Listeners.formatIdPath(descriptor)`, and `StatusListener` reads it with
`Thread.currentThread().getName()`. This ensures thread dumps and console output use
the same identifiers, making it straightforward to correlate a stuck or slow thread
with a specific test action.

**Example output** for a nested action `suite.child-step`:

```
[PARAMIXEL] AaBb-CcDd | suite.child-step
[PARAMIXEL] PASSED | AaBb-CcDd | suite.child-step 42 ms
```

The `[PARAMIXEL]` prefix uses ANSI bold-blue styling when ANSI is enabled
(`paramixel.ansi=true` or `auto` with a compatible terminal).
