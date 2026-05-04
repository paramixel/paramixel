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

`Error` subclasses (such as `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`) are **not caught** by cleanup tasks. If a cleanup task throws an `Error`, the cleanup loop aborts immediately and the error propagates. Remaining cleanup tasks will not run.

## Relationship to `Lifecycle`

A common pattern is to create resources in `Lifecycle.before` and release them in `Lifecycle.after` using `Cleanup`.

See the Testcontainers examples under `core-examples/src/main/java/examples/testcontainers/`.