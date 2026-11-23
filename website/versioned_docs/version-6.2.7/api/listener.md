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
- `onWarning(String message)`
- `onBeforeExecution(Descriptor descriptor)`
- `onAfterExecution(Descriptor descriptor)`
- `onRunCompleted(Result result)`

All methods have default no-op implementations, so custom listeners can override only what they need.

`onWarning(String)` reports conditions that do not fail the run but change behavior — for example, a
configuration key that was removed and now has no effect. The runner reports each detected condition
at most once per JVM, before execution begins.

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
the JVM thread name during execution. The execution engine assigns the thread name via
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
