---
title: Parallel Execution
description: Run independent action branches concurrently.
---

# Parallel Execution

Use `Parallel` for independent work that is safe to run concurrently.

```java
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Step.step;

Action spec = parallel("browser matrix")
        .parallelism(3)
        .child(step("chrome", ctx -> runChrome()))
        .child(step("firefox", ctx -> runFirefox()))
        .child(step("webkit", ctx -> runWebkit()))
        .build();
```

```text
browser matrix
├── chrome
├── firefox
└── webkit
```

## Runner parallelism

`paramixel.parallelism` is the runner-wide concurrency budget, and it is the only global
concurrency knob. Each executing branch — a `Parallel` child, a `Step` leaf, an `Isolated` body —
holds one unit of that budget for as long as it runs, and the thread that calls the runner holds one for the whole run, so the budget bounds both the number of
scheduler threads and the number of concurrently allocated resources such as containers.

Execution is depth-first. The thread that reaches a `Parallel` runs its first child itself and
starts additional workers for the remaining children while budget is available. Every worker simply
takes the next unstarted child, so children start in declaration order, and a child that blocks
never prevents its later siblings from starting. If no budget is free, a sibling starts as soon as
any branch elsewhere in the tree finishes and releases its budget; nothing ever waits for budget
while holding a resource. `Parallel.parallel(...).parallelism(n)` bounds how many direct children
of that `Parallel` may be in flight; it is a local window inside the global budget, and it is how
you reserve room for nested parallelism.

A `Timeout` runs its child on its own thread, which takes over the slot of the thread waiting for it, so
the deadline always applies even when the whole budget is in use. A child that ignores interruption may
keep running briefly after its deadline.

Siblings that must run at the same time (for example, children that wait on a shared latch) need
enough budget to actually run together: `paramixel.parallelism` must cover every branch that is
running concurrently, including the ones above them in the tree.

Because the budget counts branches rather than only leaf bodies, a suite that previously peaked at
`N` concurrent leaves will now peak at `N` concurrent branches. Raise `paramixel.parallelism` if a
plan needs more concurrent work.

The former `paramixel.scheduler.queue.capacity` property was removed: the scheduler no longer has a
ready queue. Setting it now logs a warning and has no effect.

## Best practices

- Share immutable data only.
- Keep external resources isolated per child.
- Avoid relying on completion order.
- Use `@Paramixel.Priority` only for discovery admission order, not as a synchronization mechanism.
- Keep cleanup in `after` actions or `CleanUp` utilities.

## Serializing subgroups with Isolated

`Isolated` serializes its body under a named lock, allowing controlled serialization within a `Parallel`. Combine with `Parallel.parallelism()` to bound concurrent execution of the body's children:

```java
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Sequential.sequential;

Action spec = parallel("suite")
        .parallelism(4)
        .child(isolated("database-tests", "db-lock")
                .body(parallel("db")
                        .parallelism(2)
                        .child(queryTest1())
                        .child(queryTest2())
                        .build())
                .build())
        .child(isolated("api-tests", "db-lock")
                .body(sequential("api")
                        .child(createUser())
                        .child(updateUser())
                        .build())
                .build())
        .build();
```

```text
suite
├── database-tests   (uses db-lock)
│   └── db
│       ├── queryTest1
│       └── queryTest2
└── api-tests        (uses db-lock)
    └── api
        ├── createUser
        └── updateUser
```

Two `Isolated` nodes share `"db-lock"`; only one body executes at a time. Each body internally controls its own parallelism via `Parallel.parallelism()`.

## Shuffled execution order

Use `.shuffle()` on `Parallel.Builder` to randomize the order children enter the rolling window, helping surface race conditions that depend on scheduling order:

```java
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Step.step;

Action spec = parallel("matrix")
        .parallelism(3)
        .shuffle()
        .child(step("chrome", ctx -> runChrome()))
        .child(step("firefox", ctx -> runFirefox()))
        .child(step("webkit", ctx -> runWebkit()))
        .build();
```

For reproducible flaky-test investigations, supply an explicit seed:

```java
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Step.step;

Action spec = parallel("matrix")
        .parallelism(3)
        .shuffle(42L)
        .child(step("chrome", ctx -> runChrome()))
        .child(step("firefox", ctx -> runFirefox()))
        .child(step("webkit", ctx -> runWebkit()))
        .build();
```
