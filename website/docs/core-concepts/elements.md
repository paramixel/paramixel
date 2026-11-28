---
title: Lifecycle and Scope
description: Fixture ownership, phases, finalization order, and failure precedence.
---

# Lifecycle and Scope

## Fixture ownership

A fixture is an ordinary object a hook creates and stores in the context. There is no instance type and no factory the
framework owns: the `before` slot builds it, the steps read it by name, and the `after` slot releases it. Data that
already exists when the plan is built is declared with `bind` instead, so it is also reported as a plan parameter.

```java
Sequential.builder(MySuite.class, "scenario")
    .bind(ENVIRONMENT, environment)
    .before(ID_START)
    .step(ID_VERIFY)
    .after(ID_STOP);

@Paramixel.Id(ID_START)
public static void start(final Context context) {
    final var fixture = new Scenario(context.require(ENVIRONMENT, Environment.class));
    context.put(FIXTURE, fixture);
    fixture.start();
}

@Paramixel.Id(ID_STOP)
public static void stop(final Context context) {
    context.find(FIXTURE, Scenario.class).ifPresent(Scenario::stop);
}
```

- The fixture belongs to the scope of the group that created it, so every descendant resolves it and parallel siblings
  never share one.
- Nothing runs at definition, build, or compile time; the hook runs when the occurrence is admitted.
- An `after` slot uses `find`, so a partially completed `before` cannot mask the original failure.
- A `before` hook that throws must clean up objects it acquired but never stored, because Paramixel can only release
  what a later `after` hook can `find`.

## Phases

```
admission → condition → before → body → child group → after → terminal
```

| Situation | Scope | Normal work | After | Terminal |
| --- | --- | --- | --- | --- |
| Never admitted | absent | never | never | `SKIPPED`/`CANCELED` with reason |
| `before` slot fails | present | body/children skipped | runs | failure preserved |
| Body fails | present | children skipped | runs | failure preserved |
| Child fails | present | dependent siblings skipped | runs | aggregate |
| `after` slot fails | present | already settled | — | cleanup failure retained |
| Success | present | normal | runs | `PASSED` |

A node has one `before` slot and one `after` slot. A slot that holds a subtree runs as an occurrence under its owner:
the console and report trees show it there with a `before`/`after` marker, and its own children beneath it. The subtree
is part of the owner's scope chain, so a value it `put`s inside a `Sequential` is visible to the owner's steps, while a
value written inside a `Parallel` branch stays in that branch.

## Scope and sharing

- A scope owner is the run root, every direct child of a `parallel` group, and every task carrying `retry` or `repeat`.
- A bound value is visible from the node that declared it downward, nearest declaration first; a `put` value wins over a
  `bind` of the same name for the rest of its scope.
- A parallel group's children each own a scope, so they never see each other's entries. The fixture decides what is
  thread-safe; Paramixel does not synchronize shared objects.
- Objects that are not thread-safe belong in separate child scopes or behind `isolated(lockName)`.

## Failure precedence

The operation's failure is primary. Later finalization failures are appended in attempt order, and the original stack
trace is never replaced. A parent aggregates `FAILED > CANCELED > ABORTED > SKIPPED > PASSED`.

## No implicit closing

Paramixel does not close a fixture merely because it implements `AutoCloseable`. Use the `after` slot so ownership and
the exact release point stay explicit and cannot double-close.
