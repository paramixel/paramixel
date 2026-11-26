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
- At `paramixel.listener.queue.capacity` (default 8192) queued events, the scheduler pauses admission and ordinary
  callbacks rather than dropping events. Task deadlines still advance. A bounded reserve sized to the compiled plan
  holds events from in-flight work, timeouts, cancellation, and cleanup until the listener catches up.
- An exception thrown by a listener is isolated and recorded as a run diagnostic; it never changes a task's status.
- `paramixel.listener.drainTimeoutMillis` (default 30000) limits how long delivery can make **no progress** while under
  backpressure or during final delivery. A slow listener that keeps making progress does not lose events. A stalled
  listener is interrupted, undelivered events are discarded, and any still-active run is cooperatively canceled.
  The returned `Result` records `LISTENER_DRAIN_TIMEOUT` and has a nonzero exit code, even if the completed tasks passed.
  An uncooperative listener may still be running; task quiescence does not imply listener quiescence.
- `onRunCompleted` runs on the dispatcher thread only after the complete event stream has been delivered. If the
  completion callback itself stalls, the same no-progress interval bounds the wait for it.
- The `Result` passed to `onRunCompleted` already carries the run's diagnostics, including infrastructure diagnostics
  such as `NON_QUIESCENT`, so console summaries and report files show them.
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
    public Optional<Duration> elapsed();
}
```

Phases: `ADMITTED`, `CONDITION`, `BEFORE`, `BODY`, `TIMEOUT`, `CHILDREN`, `AFTER`, `TERMINAL`. A complete stream delivers exactly one `TERMINAL` event per occurrence; events for
the same occurrence always respect legal phase order.
