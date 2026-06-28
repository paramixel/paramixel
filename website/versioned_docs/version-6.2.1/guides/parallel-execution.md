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

`paramixel.parallelism` controls runner-wide parallel capacity. `Parallel.parallel(...).parallelism(n)` controls a specific parallel action's child concurrency.

These two levels compose. The runner provides global scheduling capacity for the whole execution tree, while each nested `Parallel` action defines the local concurrency window for its children. This lets one test plan express a parallel top-level matrix and nested parallel work inside each branch without flattening everything into one parameterized method.

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
