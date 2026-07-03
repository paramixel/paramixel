---
title: Named Builders
description: Static factory methods for concise action tree definitions.
---

# Named Builders

Each composite action class provides a public static method named after the action
that returns its `Builder`. Terminal action classes provide named static factory
methods that return the action directly. Import statically for concise action
tree definitions.

```java
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Delay.delay;
import static org.paramixel.api.action.Delay.delayRandom;
```

## Terminal actions (named factory methods)

Terminal actions return the action directly rather than a builder:

| Action | Factory | Description |
|--------|---------|-------------|
| `Step` | `step(name, consumer)` | Terminal action wrapping user logic. |
| `Delay` | `delay(name, long)` | Fixed-duration pause in milliseconds. |
| `Delay` | `delay(name, Duration)` | Fixed-duration pause. |
| `Delay` | `delayRandom(name, long, long)` | Random-duration pause between bounds. |
| `Assert` | `assertThat(name, expected, actual)` | Boolean assertion. |
| `Assert` | `assertThat(name, expected, actual, message)` | Boolean assertion with failure message. |
| `Assert` | `assertThat(name, expected, supplier)` | Lazy boolean assertion. |
| `Assert` | `assertThat(name, expected, supplier, message)` | Lazy boolean assertion with failure message. |
| `Assert` | `assertTrue(name, actual)` | Shorthand for `assertThat(name, true, actual)`. |
| `Assert` | `assertTrue(name, supplier)` | Shorthand for `assertThat(name, true, supplier)`. |
| `Assert` | `assertFalse(name, actual)` | Shorthand for `assertThat(name, false, actual)`. |
| `Assert` | `assertFalse(name, supplier)` | Shorthand for `assertThat(name, false, supplier)`. |

## Composite actions (named builder methods)

| Action | Factory | Builder methods |
|--------|---------|----------------|
| `Scope` | `scope(name)` | `before`, `body`, `after` |
| `Sequential` | `sequential(name)` | `child`, `dependent`/`independent`, `shuffle` |
| `Parallel` | `parallel(name)` | `child`, `parallelism`, `shuffle` |
| `Repeat` | `repeat(name)` | `body`, `iterations` |
| `Conditional` | `conditional(name, condition)` | `body` |
| `Instance` | `instance(name, factory)` / `instance(type)` / `instance(name, type)` | `body` |
| `Isolated` | `isolated(name, lockName)` | `body` |
| `Timeout` | `timeout(name)` | `body`, `timeout`/`timeoutMillis` |
| `Until` | `until(name)` | `body`, `maxIterations`, `until` |

## Usage example

```java
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Assert.assertTrue;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Delay.delay;

Action tree = scope("root")
    .body(sequential("suite")
        .child(step("login", ctx -> doLogin()))
        .child(parallel("verify")
            .child(step("ui", ctx -> checkUi()))
            .child(step("api", ctx -> checkApi()))
            .build())
        .child(assertTrue("verified", true))
        .build())
    .body(delay("think-time", 200))
    .build();
```

## See also

- [Action](action) — the sealed interface and its subtypes
- [Builder](builder) — mutable builders that produce immutable action snapshots
