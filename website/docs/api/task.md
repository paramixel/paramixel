---
title: Task
description: The single immutable execution definition type.
---

# Task

```java
public sealed interface Task permits Sequential, Parallel, Repeat {
    String name();
    ExecutionMode mode();
    Optional<Pool> pool();
    boolean dependent();
    List<Task> children();
    Optional<Class<?>> anchor();
    Map<String, Object> bindings();
    Map<String, String> parameters();
    Optional<PhaseHook> before();
    Optional<PhaseHook> after();
    Optional<TaskCallback> body();
    List<String> tags();
    Optional<Long> shuffleSeed();
    Optional<Predicate<Context>> condition();
    Optional<Duration> timeout();
    Optional<RetryPolicy> retry();
    Optional<RepeatPolicy> repeat();
    Optional<String> exclusiveLock();
    boolean isEmpty();
}
```

There is no separate runtime type for `Step`, `Scope`, `Instance`, `Conditional`, `Loop`, `Timeout`, or `Isolated`.
Those concepts are attributes of a `Task`, and the three built node types — `Sequential`, `Parallel`, `Repeat` — are the
only implementations.

A `before` or `after` slot is a `PhaseHook`: either an identified hook callback or a task subtree. A subtree slot is
part of the owning node's scope chain and appears under it in the result tree.

## Guarantees

- **Immutable.** Deeply immutable structure; collections are defensively copied.
- **Lazy.** Building, compiling, and discovering never invoke a factory, lifecycle callback, body, or runtime predicate.
- **Reusable.** A definition may appear under several parents; each placement is a distinct compiled occurrence with
  distinct identity and per-run state.
- **Not for construction.** Tasks are created by a builder's `build()`; the sealed interface admits no other
  implementation.

## Related

- [Builder](../api/builder) — the typed authoring surface.
- [Result](../api/result) — the immutable execution snapshot.
- [Runner](../api/runner) — compilation, scheduling, and reporting.
