---
title: TaskBuilder
description: Typed fluent authoring with generic instance inheritance and immutable snapshots.
---

# TaskBuilder

```java
public abstract class TaskBuilder<T, SELF extends TaskBuilder<T, SELF>> {
    public SELF before(ThrowingConsumer<? super T> callback);
    public SELF before(ThrowingRunnable callback);
    public SELF after(ThrowingConsumer<? super T> callback);
    public SELF after(ThrowingRunnable callback);

    public SELF step(String name, ThrowingConsumer<? super T> operation);
    public SELF step(String name, ThrowingRunnable operation);
    public SELF step(String name, ThrowingConsumer<? super T> operation,
                     Consumer<SequentialBuilder<T>> configureChildren);
    public SELF step(String name, ThrowingRunnable operation,
                     Consumer<SequentialBuilder<T>> configureChildren);

    public SELF sequential(String name, Consumer<SequentialBuilder<T>> configure);
    public SELF parallel(String name, Pool pool, Consumer<ParallelBuilder<T>> configure);
    public SELF repeat(String name, Consumer<RepeatBuilder<T>> configure);
    public SELF when(String name, Predicate<Context> condition, Consumer<SequentialBuilder<T>> configure);
    public SELF onlyIf(Predicate<Context> condition);

    public <E> SELF each(Iterable<? extends E> items,
                         Function<? super E, ? extends TaskBuilder<?, ?>> mapper);
    public SELF child(Task child);
    public SELF child(TaskBuilder<?, ?> child);
    public SELF shuffle();
    public SELF shuffle(long seed);
    public SELF tags(String... values);
    public SELF timeout(Duration timeout);
    public SELF retry(RetryPolicy policy);
    public SELF isolated(String lockName);
    public <R> SELF resource(Key<R> key, ThrowingSupplier<? extends R> factory);
    public <R> SELF resource(Key<R> key, ThrowingSupplier<? extends R> factory,
                             ThrowingConsumer<? super R> release);
    public Task build();
}
```

## The type parameter

`T` is the **nearest lexical primary instance**: `Void` means no instance binding. A nested group inherits the enclosing
`T` without creating an instance, and a nested `forInstance` establishes its own binding.

```java
forInstance("scenario", () -> new Scenario(environment))
    .before(Scenario::start)
    .sequential("checks", checks -> checks
        .step("health", Scenario::checkHealth)
        .step("metrics", Scenario::checkMetrics))
    .after(Scenario::stop);
```

No `Scenario.class` token appears at any level, and nested callbacks keep the same fluent type.

## Builder rules

- `before` callbacks run in declaration order until one fails; `after` callbacks run in **reverse** declaration order and
  are all attempted.
- There is no hidden "current step". Children are attached with the three-argument `step`, which takes an explicit
  child-configuration callback.
- `each` expands eagerly at definition time; a null mapping fails the definition immediately.
- Adding a builder snapshots it: later mutation of the child cannot change an attached subtree.
- `build()` returns a deep snapshot, may be called repeatedly, and performs no user work.

## Compile-time guidance

- `.independent()` exists only on `SequentialBuilder`.
- `parallel(name, Pool.named("x"), …)` requires a `Pool`; a second `String` does not compile.
- No-argument callbacks compile on a builder with no instance binding.
- The explicit context-aware methods (`beforeWithContext`, `stepWithContext`, …) avoid ambiguous overloads for
  one-argument method references.

## Next

- [Named builders](../api/named-builders) — `sequential`, `parallel`, and repetition views.
- [Result](../api/result) — what a run produces.
