---
title: Quick Reference
description: The 7.0 authoring and execution surface on one page.
---

# Quick Reference

## Imports

```java
import static org.paramixel.api.Tasks.*;
import org.paramixel.api.*;
import org.paramixel.api.support.*;
```

## Authoring

```java
Task definition = parallel("Matrix", Pool.named("environments"))
        .each(environments, env -> forInstance(env.name(), () -> new Probe(env))
                .before(Probe::start)
                .sequential("checks", checks -> checks
                        .independent()
                        .step("health", Probe::checkHealth)
                        .step("metrics", Probe::checkMetrics))
                .after(Probe::stop))
        .build();
```

| Call | Meaning |
| --- | --- |
| `task(name)` / `sequential(name)` | Sequential container |
| `parallel(name, Pool.named("p"))` | Parallel container; direct children hold one permit each |
| `forInstance(name, supplier)` | Owns one instance per attempt |
| `.before(cb)` / `.after(cb)` | Lifecycle on the owning task; `after` runs in reverse order |
| `.step(name, op)` | Child step; returns the enclosing builder |
| `.step(name, op, children -> …)` | Child step with its own sequential subtree |
| `.independent()` / `.dependent()` | Sequential failure continuation (parallel has neither) |
| `.when(name, predicate, …)` / `.onlyIf(predicate)` | Runtime condition |
| `.repeat(name, r -> r.maxIterations(n)…)` | Bounded replay |
| `.retry(RetryPolicy…)` | Bounded re-attempt |
| `.timeout(Duration)` | Cooperative deadline |
| `.isolated("lock")` | Exclusive logical lock |
| `.resource(Key, factory[, release])` | Secondary resource |
| `.each(items, mapper)` | Eager typed expansion |
| `.shuffle(seed)` / `.tags(…)` | Ordering and metadata |
| `.build()` | Immutable snapshot |

## Running

```java
try (Runner runner = Runner.builder()
        .configuration(Configuration.defaultConfiguration())
        .pool(Pool.named("environments"), 3)
        .listener(Listener.defaultListener(Configuration.defaultConfiguration()))
        .build()) {
    Result result = runner.run(definition);
    System.exit(result.exitCode());
}
```

## Statuses

`PASSED`, `FAILED`, `SKIPPED`, `ABORTED`, `CANCELED`; severity
`FAILED > CANCELED > ABORTED > SKIPPED > PASSED`.

## Signals

```java
throw FailException.fail("assertion");
throw SkipException.skip("not applicable");
throw AbortException.abort("precondition");
```

## Configuration

| Key | Default |
| --- | --- |
| `paramixel.parallelism` | processors |
| `paramixel.parallelism.<pool>` | required for user pools |
| `paramixel.workers` | processors |
| `paramixel.shutdown.graceMillis` | `10000` |
| `paramixel.failureOnSkip` / `failureOnAbort` | `true` |
| `paramixel.failIfNoTests` | `true` |
| `paramixel.failFast` | `false` |
