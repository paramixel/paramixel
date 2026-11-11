---
title: Task
description: The single immutable execution definition type.
---

# Task

```java
public final class Task {
    public String name();
    public ExecutionMode mode();
    public Optional<Pool> pool();
    public boolean dependent();
    public List<Task> children();
    public List<TaskCallback> before();
    public List<TaskCallback> after();
    public Optional<TaskCallback> body();
    public Optional<TaskFactory> instanceFactory();
    public boolean requiresPrimaryInstance();
    public List<String> tags();
    public Optional<Long> shuffleSeed();
    public Optional<Predicate<Context>> condition();
    public Optional<Duration> timeout();
    public Optional<RetryPolicy> retry();
    public Optional<RepeatPolicy> repeat();
    public Optional<String> exclusiveLock();
    public List<ResourceDeclaration> resources();
    public boolean isEmpty();
}
```

There is no separate runtime type for `Step`, `Sequential`, `Parallel`, `Scope`, `Instance`, `Conditional`, `Loop`,
`Timeout`, or `Isolated`. Those concepts are attributes of a `Task`.

## Guarantees

- **Immutable.** Deeply immutable structure; collections are defensively copied.
- **Lazy.** Building, compiling, and discovering never invoke a factory, lifecycle callback, body, or runtime predicate.
- **Reusable.** A definition may appear under several parents; each placement is a distinct compiled occurrence with
  distinct identity and per-run state.
- **Not for construction.** Tasks are created by `TaskBuilder.build()` or the `Tasks` facade.

## Related

- [Builder](../api/builder) — the typed authoring surface.
- [Result](../api/result) — the immutable execution snapshot.
- [Runner](../api/runner) — compilation, scheduling, and reporting.
