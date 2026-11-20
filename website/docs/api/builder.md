---
title: TaskBuilder
description: Typed fluent authoring with generic instance inheritance and immutable snapshots.
---

# TaskBuilder

```java
public abstract class TaskBuilder<T, S extends TaskBuilder<T, S>> {
    public S before(ThrowingConsumer<? super T> callback);
    public S before(ThrowingRunnable callback);
    public S after(ThrowingConsumer<? super T> callback);
    public S after(ThrowingRunnable callback);

    public S step(String name, ThrowingConsumer<? super T> operation);
    public S step(String name, ThrowingRunnable operation);
    public S step(String name, ThrowingConsumer<? super T> operation,
                     Consumer<SequentialBuilder<T>> configureChildren);
    public S step(String name, ThrowingRunnable operation,
                     Consumer<SequentialBuilder<T>> configureChildren);

    public S sequential(String name, Consumer<SequentialBuilder<T>> configure);
    public S parallel(String name, Pool pool, Consumer<ParallelBuilder<T>> configure);
    public S repeat(String name, Consumer<RepeatBuilder<T>> configure);
    public S when(String name, Predicate<Context> condition, Consumer<SequentialBuilder<T>> configure);
    public S onlyIf(Predicate<Context> condition);

    public <E> S each(Iterable<? extends E> items,
                         Function<? super E, ? extends TaskBuilder<?, ?>> mapper);
    public S child(Task child);
    public S child(TaskBuilder<?, ?> child);
    public S shuffle();
    public S shuffle(long seed);
    public S tags(String... values);
    public S timeout(Duration timeout);
    public S retry(RetryPolicy policy);
    public S isolated(String lockName);
    public <R> S resource(Key<R> key, ThrowingSupplier<? extends R> factory);
    public <R> S resource(Key<R> key, ThrowingSupplier<? extends R> factory,
                             ThrowingConsumer<? super R> release);
    public Task build();
}
```

## The type parameter

`T` is the **nearest lexical primary instance**: `Void` means no instance binding. A nested group inherits the enclosing
`T` without creating an instance, and a nested `instance` establishes its own binding.

```java
instance("scenario", () -> new Scenario(environment))
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
