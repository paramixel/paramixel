---
title: TaskBuilder
description: Typed fluent authoring with generic instance inheritance and immutable snapshots.
---

# TaskBuilder

```java
public abstract class TaskBuilder<S extends TaskBuilder<S>> {
    public S before(String id);
    public S after(String id);
    public S step(String id);
    public S body(String id);
    public S onlyIf(String id);

    public S bind(String name, Object value);
    public S child(Task child);
    public S child(TaskBuilder<?> child);
    public S children(Collection<? extends TaskBuilder<?>> builders);

    public S shuffle();
    public S shuffle(long seed);
    public S tags(String... values);
    public S timeout(Duration timeout);
    public S retry(RetryPolicy policy);
    public S isolated(String lockName);
    public Task build();
}
```

Every hook is an ordinary `public static` method named by a stable identifier, and the tree refers to it by that
identifier. There is no functional-interface overload and no generic instance type.

## Anchors and identifiers

```java
public class ScenarioTest {

    private static final String ID_START = "start";
    private static final String ID_HEALTH = "health";
    private static final String ID_STOP = "stop";
    private static final String FIXTURE = "fixture";

    @Paramixel.Factory
    public static Task factory() {
        return sequential(ScenarioTest.class, "ScenarioTest")
                .bind(FIXTURE, new Scenario())
                .before(ID_START)
                .child(sequential("checks").step(ID_HEALTH))
                .after(ID_STOP)
                .build();
    }

    @Paramixel.Id(ID_START)
    public static void start(final Context context) {
        context.require(FIXTURE, Scenario.class).start();
    }
}
```

The class literal appears **once per definition root**; everything attached below it inherits it. Identifiers resolve
only against methods declared by that class — inheritance is not consulted — and they are also the labels reported for
the hooks (`before[start]`, `body[health]`).

## Builder rules

- Identifiers are resolved when the definition is built, before any hook runs; every problem in one definition is
  reported together as a `CompilationException`.
- `before` hooks run in declaration order until one fails; `after` hooks run in **reverse** declaration order and are
  all attempted.
- There is no hidden "current step". `.step(id)` adds a body-only child; a child that needs **both** a body and
  children is written `child(sequential(name).body(id)…)`.
- `.body(id)` attaches a body to the occurrence the builder already describes, which is how a body-bearing step keeps its
  one-occurrence shape.
- `bind` seeds the scope at the start of every attempt; a name must be unique on its own task.
- Adding a builder snapshots it when it references no identifier; one that does is materialized when the anchor is
  known, so treat it as immutable after attachment.
- `build()` returns a deep snapshot, may be called repeatedly, and performs no user work.

## Compile-time guidance

- `.independent()` exists only on `SequentialBuilder`.
- `parallel(name, Pool.named("x"))` requires a `Pool`; a second `String` does not compile.
- `Tasks.instance(...)` and the callback overloads no longer exist: authoring goes through identifiers.
- An identifier used in a definition with no anchor is rejected when the definition is built.


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
