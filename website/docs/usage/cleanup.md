---
title: Cleanup
description: Manage explicit cleanup work.
---

# Cleanup

`org.paramixel.core.support.Cleanup` is a small utility for collecting cleanup tasks and running them later.

## Main features

- forward or reverse execution order
- add plain callbacks
- add `AutoCloseable` instances
- conditional cleanup with `addWhen(...)`
- inspect failures via `CleanupResult`
- throw the first failure with remaining failures suppressed via `runAndThrow()`

## Example

```java
Cleanup cleanup = new Cleanup(Cleanup.Mode.REVERSE)
        .add(() -> stopServer())
        .addCloseable(network);

CleanupResult result = cleanup.run();
```

## Throwing aggregated failures

```java
new Cleanup(Cleanup.Mode.FORWARD)
        .addCloseable(resourceA)
        .addCloseable(resourceB)
        .runAndThrow();
```

`runAndThrow()` throws the first cleanup failure and attaches later failures as suppressed exceptions.

## Relationship to `Lifecycle`

A common pattern is to create resources in `Lifecycle.before` and release them in `Lifecycle.after` using `Cleanup`.

See the Testcontainers examples under `examples/src/main/java/examples/testcontainers/`.
