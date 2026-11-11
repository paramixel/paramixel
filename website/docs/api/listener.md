---
title: Listener
description: Event delivery, isolation, ordering, and bounded buffering.
---

# Listener

```java
public interface Listener {
    default void onRunStarted(Configuration configuration) {}
    default void onDiscoveryStarted() {}
    default void onDiscoveryCompleted(Optional<Task> root) {}
    default void onWarning(String message) {}
    default void onTaskEvent(TaskEvent event) {}
    default void onRunCompleted(Result result) {}
}
```

`Listener.NOOP` ignores everything; `Listener.defaultListener(configuration)` returns the standard console chain plus a
report-file writer when `paramixel.report.file` is set.

## Delivery model

- Events are produced by the scheduler and delivered by one dispatcher thread, so listener code never runs while the
  scheduler holds a permit or lock.
- The queue is bounded (`paramixel.listener.queue.capacity`, default 8192). A full queue applies backpressure instead of
  dropping events.
- An exception thrown by a listener is isolated and recorded as a run diagnostic; it never changes a task's status.
- `onRunCompleted` runs after all task events are drained, so reporting cannot race finalization.
- A listener must not re-enter the running `Runner`.

## TaskEvent

```java
public final class TaskEvent {
    public long sequence();                 // monotonic per run
    public String runId();
    public String occurrenceId();
    public Optional<String> parentOccurrenceId();
    public String name();
    public String path();
    public TaskPhase phase();
    public int attempt();
    public Status status();
    public Instant timestamp();
}
```

Phases: `ADMITTED`, `CONDITION`, `RESOURCES_ACQUIRED`, `BEFORE`, `BODY`, `CHILDREN`, `TIMEOUT`, `AFTER`,
`RESOURCES_RELEASED`, `TERMINAL`. Exactly one `TERMINAL` event is delivered per occurrence; events for the same
occurrence always respect legal phase order.
