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

## Run methods

- `run(Action)` returns `Result`.
- `run(Selector)` returns `Optional<Result>`.
- `runAndReturnExitCode(Action)` and `runAndReturnExitCode(Selector)` return `0` or `1`.
- `runAndExit(...)` terminates the JVM with the derived code.
- `run()` discovers factories from the classpath and returns an exit code.

`Runner.main(String[])` calls `Runner.builder().build().run()` and exits with the returned code.
