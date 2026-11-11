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
```

## Terminal actions (named factory methods)

Terminal actions return the action directly rather than a builder:

| Action | Factory | Description |
|--------|---------|-------------|
| `Step` | `step(name, consumer)` | Terminal action wrapping user logic. |
| `Step` | `step(name, type, consumer)` | Terminal action that receives the enclosing fixture. |

## Composite actions (named builder methods)

| Action | Factory | Builder methods |
|--------|---------|----------------|
| `Scope` | `scope(name)` | `before`, `body`, `after` |
| `Sequential` | `sequential(name)` | `child`, `children`, `each`, `dependent`/`independent`, `shuffle` |
| `Parallel` | `parallel(name, pool)` | `child`, `children`, `each`, `shuffle` |
| `Conditional` | `conditional(name, condition)` | `body` |
| `Instance` | `of(name, type, factory)` / `of(type)` / `of(name, type)` | `before`, `body`, `after` |
| `Isolated` | `isolated(name, lockName)` | `body` |
| `Loop` | `loop(name)` | `body`, `maxIterations`, `until`, `untilPassed`, `delay(Backoff)` |
| `Timeout` | `timeout(name)` | `body`, `timeout` |

## Usage example

```java
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

Action tree = scope("root")
    .body(sequential("suite")
        .child(step("login", ctx -> doLogin()))
        .child(parallel("verify", "examples")
            .child(step("ui", ctx -> checkUi()))
            .child(step("api", ctx -> checkApi()))
            .build())
        .child(step("verified", ctx -> {
            if (!isVerified()) {
                throw fail("not verified");
            }
        }))
        .child(step("think-time", ctx -> Thread.sleep(200)))
        .build())
    .build();
```

## See also

- [Action](action) — the sealed interface and its subtypes
- [Builder](builder) — mutable builders that produce immutable action snapshots
