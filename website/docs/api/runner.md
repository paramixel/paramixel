---
title: Runner
description: Compile a definition, execute it with bounded concurrency, and report the result.
---

# Runner

```java
Runner runner = Runner.builder()
        .configuration(Configuration.defaultConfiguration())
        .pool(Pool.named("environments"), 3)
        .listener(listener)
        .build();

Result result = runner.run(definition);       // no discovery, no hooks
Result discovered = runner.run(selector);     // discover exactly this selection
Result configured = runner.run();             // use the configured selection
int exitCode = result.exitCode();
```

`Runner` is `AutoCloseable`. `close()` requests cooperative cancellation of an active run and is idempotent; a closed
runner rejects further runs.

## Entry points

| Method | Behavior |
| --- | --- |
| `run(Task)` | Executes the supplied definition. No classpath scan and no `@BeforeAll`/`@AfterAll`. |
| `run(Selector)` | Discovers factories with exactly that selector. A configured `paramixel.match.*` key the selector does not use is reported as a warning instead of being merged silently. |
| `run()` | Discovers with the configured selection. |

## Contract

- **One run at a time per instance.** A concurrent call throws `IllegalStateException`; independent runners may run
  concurrently and share no mutable framework state.
- **Validation first.** Invalid configuration throws `ConfigurationException`; an invalid definition or plan throws
  `CompilationException`; discovery problems throw `ResolverException`. In every case no factory, lifecycle callback, or
  body has run.
- **Synchronous completion.** A normal run returns after every occurrence settled and every finalizer ran.
- **Non-quiescent runs.** A run whose callback ignored cancellation past the grace period returns with
  `result.isQuiescent() == false`, withheld cleanup, and a non-zero exit code.
- **Pool capacity precedence.** `Runner.Builder.pool(Pool, int)` → `paramixel.parallelism.<name>` → error for a user
  pool. `paramixel.workers` separately bounds execution threads.

## Builder

```java
Runner.Builder builder = Runner.builder()
        .configuration(configuration)
        .pool(Pool.named("environments"), 3)
        .listener(listener)
        .shuffle(seed);          // or shuffle() for a seed chosen at build time
```

`shuffle` affects discovery ordering only; a direct definition controls its own shuffle. The effective seed is reported
in `Result.shuffleSeed()`.

## Next

- [Result](../api/result) — the outcome tree.
- [Listener](../api/listener) — event delivery.
