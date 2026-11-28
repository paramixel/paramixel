---
title: Quick Reference
description: The 7.0 authoring and execution surface on one page.
---

# Quick Reference

## Imports

```java
import org.paramixel.api.*;
import org.paramixel.api.exception.*;
import org.paramixel.api.selector.*;
import org.paramixel.api.support.*;
```

## Authoring

```java
final var root = Parallel.builder(MySuite.class, "Matrix", Pool.named("environments"));
for (final var env : environments) {
    root.step(Sequential.builder(env.name())
            .bind("environment", env)
            .before("start")
            .step(Sequential.builder("checks")
                    .step("health")
                    .step("metrics"))
            .after("stop"));
}
Parallel definition = root.build();

@Paramixel.Id("health")
public static void health(final Context context) {
    context.require("environment", Environment.class).checkHealth();
}
```

| Call | Meaning |
| --- | --- |
| `Sequential.builder(name)` / `Sequential.builder(Owner.class, name)` | Sequential node, unanchored or anchored for hook ids |
| `Parallel.builder(name, Pool.named("p"))` | Parallel node; direct children hold one permit each |
| `Repeat.builder(name).maxIterations(n)` | Bounded replay with an optional `until(...)` |
| `.bind(name, value)` | Plan parameter: a value that already exists at build time |
| `.before(id)` / `.before(Task or builder)` | One setup slot; compose several phases with a sequential subtree |
| `.after(id)` / `.after(Task or builder)` | One cleanup slot; runs once the node has started, whatever failed |
| `.step(id)` | Body-only child step; `.body(id)` puts a body on the current occurrence |
| `.step(Task or builder)` | Child subtree, materialized when the parent is built |
| `.independent()` / `.dependent()` | Sequential failure continuation (parallel has neither) |
| `.onlyIf(id)` | Runtime condition, resolved hook must return `boolean` |
| `.retry(RetryPolicy…)` | Bounded re-attempt |
| `.timeout(Duration)` | Cooperative deadline |
| `.isolated("lock")` | Exclusive logical lock |
| `.shuffle(seed)` / `.tags(…)` | Ordering and metadata |
| `.build()` | Immutable snapshot of the concrete node type |

## Running

```java
final var configuration = Configuration.defaultConfiguration();
final int exitCode;
try (Runner runner = Runner.builder()
        .configuration(configuration)
        .pool(Pool.named("environments"), 3)
        .listener(Listener.defaultListener(configuration))
        .build()) {
    exitCode = runner.run(definition).exitCode();
}
System.exit(exitCode);
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

The remaining keys — `paramixel.ansi`, `paramixel.report.file`, `paramixel.match.*`, and `paramixel.listener.*` — are
listed in [Properties](../configuration/properties).
