---
title: Lifecycle and Scope
description: Instance ownership, phases, finalization order, and failure precedence.
---

# Lifecycle and Scope

## Fixture ownership

A fixture is an ordinary object a hook creates and stores in the context. There is no instance type and no factory the
framework owns: the `before` hook builds it, the steps read it by name, and an `after` hook releases it.

```java
sequential(MySuite.class, "scenario")
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
- An `after` hook uses `find`, so a partially completed `before` cannot mask the original failure.
- A factory that throws must clean up its own partially acquired objects, because Paramixel cannot reach an object that
  was never returned.

## Phases

```
admission → resources → instance → [condition] → before → [body] → child group → after → release → terminal
```

| Situation | Instance | Normal work | After | Terminal |
| --- | --- | --- | --- | --- |
| Never admitted | absent | never | never | `SKIPPED`/`CANCELED` with reason |
| Factory throws / null | absent | never | never | `FAILED` |
| `before` fails | present | body/children skipped | all attempted | failure preserved |
| Body fails | present | children skipped | all attempted | failure preserved |
| Child fails | present | dependent siblings skipped | all attempted | aggregate |
| `after` fails | present | already settled | remaining attempted | cleanup failure retained |
| Success | present | normal | all | `PASSED` |

Multiple `before` callbacks run in declaration order until one fails; remaining ones are recorded `SKIPPED`. Multiple
`after` callbacks run in reverse declaration order, and every one is attempted.

## Scope and sharing

- A primary instance belongs to its occurrence. Reused definitions produce independent instances per environment.
- A parallel group's children share the inherited instance and are concurrent calls on it. The fixture decides what is
  thread-safe; Paramixel does not synchronize typed callbacks.
- Resources that are not thread-safe belong in separate child scopes or behind `isolated(lockName)`.

## Failure precedence

The operation's failure is primary. Later finalization failures are appended in attempt order, and the original stack
trace is never replaced. A parent aggregates `FAILED > CANCELED > ABORTED > SKIPPED > PASSED`.

## No implicit closing

Paramixel does not close a fixture merely because it implements `AutoCloseable`. Use an `after` callback so ownership and
the exact release point stay explicit and cannot double-close.
