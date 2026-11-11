---
title: Runner
description: Entry points for executing action trees and discovered factories.
---

# Runner

`Runner` executes one run and returns a `Result`. There is one entry point per intent.

## Create a runner

```java
var runner = Runner.builder().build();

var custom = Runner.builder()
        .configuration(configuration)
        .listener(listener)
        .build();
```

Builders are reusable; calling `build()` produces a new runner from the builder's current state.

## Entry points

| Method | What it does |
| ------ | ------------ |
| `run(Action)` | Executes exactly the supplied tree. No discovery, no classpath scan, no `@BeforeAll`/`@AfterAll` hooks. |
| `run(Selector)` | Discovers factories with exactly the supplied selector and executes them. |
| `run()` | Discovers factories with `Selector.configured()` (the `paramixel.match.*` keys). |

Every entry point returns a `Result`; `Result.exitCode()` is the single exit-code authority:

```java
System.exit(Runner.builder().build().run().exitCode());
```

`Launcher.main(String[])` does exactly that for command-line use.

## Direct trees

A direct tree is the complete definition, so nothing is scanned and no hook runs. Setup and teardown around a tree
is a `Scope`:

```java
var tree = Scope.scope("suite")
        .before(step("setup", context -> start()))
        .body(sequential("tests").child(step("test", context -> verify())))
        .after(step("teardown", context -> stop()))
        .build();

runner.run(tree);
```

`paramixel.failFast` applies to the direct children of the root, which for a direct tree is the tree's own first
level.

## Discovery runs and hooks

`run()` and `run(Selector)` discover `@Paramixel.Factory` methods across the classpath. `@BeforeAll` and
`@AfterAll` hooks are global in those runs: they are found across the whole classpath, not just in the selected
packages, because they wrap the entire run rather than individual factories.

The selector is used as given. When a `paramixel.match.*` key is set but the selector passed to `run(Selector)`
does not use `Selector.configured()`, the runner reports it through `Listener.onWarning` instead of merging the key
silently. Use `Selector.configured().and(mine)` to combine the configured filter with your own.

## Shuffled discovery ordering

```java
var runner = Runner.builder().shuffle().build();       // random seed
var reproducible = Runner.builder().shuffle(42L).build();
```

Shuffle applies only to discovery-based execution (`run(Selector)` and `run()`). For a direct tree, use
`Parallel.parallel(...).shuffle()` or `Sequential.sequential(...).shuffle()` on the builder.
