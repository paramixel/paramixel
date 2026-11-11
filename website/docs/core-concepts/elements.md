---
title: Lifecycle and Scope
description: Instance ownership, phases, finalization order, and failure precedence.
---

# Lifecycle and Scope

## Instance ownership

`forInstance(name, supplier)` makes a task the owner of exactly one primary instance per execution attempt. The supplier
runs once per attempt, on an execution worker, only after admission and permit acquisition.

```java
forInstance("scenario", () -> new Scenario(environment))
    .before(Scenario::start)
    .step("verify", Scenario::verify)
    .after(Scenario::stop);
```

- A nested group inherits the nearest binding; a nested `forInstance` establishes its own.
- The factory never runs at definition, build, or compile time.
- A factory that returns `null` fails the occurrence; no lifecycle callback runs.
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
