---
title: Parallel Execution
description: Run independent action branches concurrently.
---

# Parallel Execution

Use `Parallel` for independent work that is safe to run concurrently.

```java
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;

Action spec = Parallel.builder("browser matrix")
        .parallelism(3)
        .child(Step.of("chrome", ctx -> runChrome()))
        .child(Step.of("firefox", ctx -> runFirefox()))
        .child(Step.of("webkit", ctx -> runWebkit()))
        .build();
```

## Runner parallelism

`paramixel.parallelism` controls runner-wide parallel capacity. `Parallel.builder(...).parallelism(n)` controls a specific parallel action's child concurrency.

## Best practices

- Share immutable data only.
- Keep external resources isolated per child.
- Avoid relying on completion order.
- Use `@Paramixel.Priority` only for discovery admission order, not as a synchronization mechanism.
- Keep cleanup in `after` actions or `CleanUp` utilities.
