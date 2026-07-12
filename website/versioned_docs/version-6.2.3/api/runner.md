---
title: Runner
description: Entry point for executing action trees.
---

# Runner

`Runner` executes action trees and discovered factories.

## Create a runner

```java
var runner = Runner.defaultRunner();

var custom = Runner.builder()
        .configuration(configuration)
        .listener(listener)
        .build();
```

Builders are reusable; calling `build()` produces a new runner from the builder's current configuration.

## Shuffled discovery ordering

`Runner.Builder` supports shuffling the order of discovered action trees to surface test order dependencies:

```java
var runner = Runner.builder()
        .shuffle()
        .build();
```

`.shuffle()` generates a seed from `ThreadLocalRandom` when `build()` is called. For reproducible ordering, use `.shuffle(long seed)`:

```java
var runner = Runner.builder()
        .shuffle(42L)
        .build();
```

Runner shuffle applies only to discovery-based execution via `run(Selector)` and `run()`. Direct execution via `run(Action)` is unaffected — use `Parallel.parallel(...).shuffle()` or `Sequential.sequential(...).shuffle()` on individual action trees instead.

## Run methods

- `run(Action)` returns `Result`.
- `run(Selector)` returns `Optional<Result>`.
- `runAndReturnExitCode(Action)` and `runAndReturnExitCode(Selector)` return `0` or `1`.
- `runAndExit(...)` terminates the JVM with the derived code.
- `run()` discovers factories from the classpath and returns an exit code.

`Runner.main(String[])` calls `Runner.builder().build().run()` and exits with the returned code.
