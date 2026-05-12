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
Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE)
        .add(() -> stopServer())
        .addCloseable(network);

CleanupResult result = cleanup.run();
```

## Throwing aggregated failures

```java
Cleanup.of(Cleanup.Mode.FORWARD)
        .addCloseable(resourceA)
        .addCloseable(resourceB)
        .runAndThrow();
```

`runAndThrow()` throws the first cleanup failure and attaches later failures as suppressed exceptions.

## Re-running and clearing

By default, a `Cleanup` can only be run once. Calling `run()` a second time throws `IllegalStateException`.

- **`reset()`** resets the execution state so the same registered tasks can be run again. Registered executables are preserved.

```java
Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE)
        .add(() -> stopServer())
        .addCloseable(network);

CleanupResult result = cleanup.run();
// ... inspect result ...

cleanup.reset();              // preserved executables can now be re-run
CleanupResult result2 = cleanup.run();
```

- **`clear()`** clears all registered executables and resets the execution state. Use this to reuse the same instance from scratch.

```java
Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
        .add(() -> stopServer());

cleanup.run();
cleanup.clear();              // executables removed, can add new ones
cleanup.add(() -> stopOther()).run();
```

## Fatal errors

`OutOfMemoryError` and `StackOverflowError` are **not caught** by cleanup tasks. If a cleanup task throws either error, the cleanup loop aborts immediately and the error propagates. Other `Error` subclasses are captured in `CleanupResult` like other cleanup failures.

## Thread safety

`Cleanup` is **not thread-safe**. Instances must not be shared across threads or accessed concurrently. Typical usage confines a `Cleanup` instance to a single lifecycle method pair such as `before`/`after`.

## Relationship to `Container`

A common pattern is to create resources in `Container.before(...)` and release them in `Container.after(...)` using `Cleanup`.

See the Testcontainers examples under `examples/src/main/java/examples/testcontainers/`.
