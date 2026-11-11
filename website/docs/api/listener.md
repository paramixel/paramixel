---
title: Listener
description: Callback interface for discovery and execution events.
---

# Listener

`Listener` receives lifecycle callbacks during discovery and execution.

## Callbacks

- `onRunStarted(Configuration)`
- `onDiscoveryStarted()`
- `onDiscoveryCompleted(Optional<Descriptor> root)`
- `onWarning(String)`
- `onBeforeExecution(Descriptor descriptor)`
- `onAfterExecution(Descriptor descriptor)`
- `onRunCompleted(Result result)`

All methods have default no-op implementations, so a custom listener overrides only what it needs.

```java
Listener listener = new Listener() {
    @Override
    public void onRunCompleted(Result result) {
        System.out.println(result.status().kind() == Status.Kind.PASSED ? "PASSED" : "NOT PASSED");
    }
};

var runner = Runner.builder().listener(listener).build();
```

`onRunStarted` is the first callback of a run and receives the effective configuration, so a listener configures
itself there. `onDiscoveryCompleted` receives an empty optional when a discovery run finds nothing.

`onBeforeExecution` and `onAfterExecution` are invoked **concurrently** from pool worker threads, because a
`Parallel` runs its children at the same time. Implementations must be thread-safe.

`onWarning` reports conditions that would otherwise be silent: a `paramixel.match.*` key the selector passed to
`run(Selector)` does not use, and an unknown `paramixel.*` key.

`Listener.defaultListener(configuration)` creates the framework's console/report listener chain; `Runner.builder()`
wires it for you.

## Console output

The default chain includes `StatusListener`, which prints an execution-start line before each descriptor runs and a
completion line after it finishes.

**Execution-start format:**

```
[PARAMIXEL] <id-path> | <display-name-path>
```

**Completion format:**

```
[PARAMIXEL] <status> | <id-path> | <display-name-path> <elapsed-ms> [→ cause]
```

The `<id-path>` is the descriptor ID path (hyphen-joined chain of four-character descriptor IDs from root to leaf)
and is also the JVM thread name during execution, so a thread dump and the console use the same identifiers.

**Example** for a nested action `suite.child-step`:

```
[PARAMIXEL] AaBb-CcDd | suite.child-step
[PARAMIXEL] PASSED | AaBb-CcDd | suite.child-step 42 ms
```

The `[PARAMIXEL]` prefix uses ANSI bold-blue styling when ANSI is enabled (`paramixel.ansi=true` or `auto` with a
compatible terminal).

The summary listener prints the tree, the status footer, and a `Pools` line with each pool's peak usage and size.
