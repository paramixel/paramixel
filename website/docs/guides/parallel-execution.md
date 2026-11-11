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

Action spec = parallel("browser matrix", "examples")
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

## Pools

Every `Parallel` names a **pool**: a run-wide concurrency budget whose size is configuration.

```properties
# the root pool: the top-level Parallel Paramixel builds when it discovers your factories
paramixel.parallelism=8
# a named pool
paramixel.parallelism.docker=4
# a pool sized from the CPU count: @cores, @cores*N or @cores/N (never below 1)
paramixel.parallelism.environments=@cores
```

```java
parallel("environments", "docker").each(environments, env -> ...)
```

Each direct child takes one permit from its pool before it starts and returns it when its whole subtree is
finished, so across every `Parallel` that names `docker`, at most four children are alive at once, including
their `before` and `after` actions. This is how a guarantee such as "no more than four environments exist at a
time" is expressed and enforced.

- **Children are admitted in declaration order**, and a later sibling waits only for a permit, never for an
  earlier sibling to finish. A pool of size one runs its children serially, in order.
- **Execution is depth-first**: a child's subtree runs (`before`, then its children, then `after`) with listener
  events nested accordingly, each descriptor on one thread.
- **Name pools for what they limit** (`docker`, `environments`, `database`), not for where they sit in the tree.
- **A pool must be defined.** An undefined pool fails the run before anything executes and names the key to add.
  A pool may be defined before anything uses it.
- **A pool cannot be nested under itself.** An inner `Parallel` needs its own pool. The runner also rejects, up
  front, pools and `Isolated` locks that could be taken in conflicting order in different branches, so a plan
  that could deadlock on them does not start. It cannot see locks you take inside a `Step`.
- `paramixel.parallelism` sizes only the root pool; it does not bound nested `Parallel` nodes.

A `Timeout` runs its child on its own thread so the deadline always applies. A child that ignores interruption may
keep running after its deadline, and until it exits it keeps the pool permits of its subtree.

## Best practices

- Share immutable data only.
- Keep external resources isolated per child.
- Avoid relying on completion order.
- Use `@Paramixel.Priority` only for discovery admission order, not as a synchronization mechanism.
- Keep cleanup in `after` actions or `CleanUp` utilities.

## Serializing subgroups with Isolated

`Isolated` serializes its body under a named lock, allowing controlled serialization within a `Parallel`. Give the inner `Parallel` its own pool to bound concurrent execution of the body's children:

```java
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Sequential.sequential;

Action spec = parallel("suite", "examples")
        .child(isolated("database-tests", "db-lock")
                .body(parallel("db", "examples")
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

Two `Isolated` nodes share `"db-lock"`; only one body executes at a time. Each body bounds its own parallelism through the pool its `Parallel` names.

## Shuffled execution order

Use `.shuffle()` on `Parallel.Builder` to randomize the order children enter the rolling window, helping surface race conditions that depend on scheduling order:

```java
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Step.step;

Action spec = parallel("matrix", "examples")
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

Action spec = parallel("matrix", "examples")
        .shuffle(42L)
        .child(step("chrome", ctx -> runChrome()))
        .child(step("firefox", ctx -> runFirefox()))
        .child(step("webkit", ctx -> runWebkit()))
        .build();
```
