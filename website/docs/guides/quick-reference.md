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
final var root = parallel(MySuite.class, "Matrix", Pool.named("environments"));
for (final var env : environments) {
    root.child(sequential(env.name())
            .bind("environment", env)
            .before("start")
            .child(sequential("checks")
                    .step("health")
                    .step("metrics"))
            .after("stop"));
}
Task definition = root.build();

@Paramixel.Id("health")
public static void health(final Context context) {
    context.require("environment", Environment.class).checkHealth();
}
```

| Call | Meaning |
| --- | --- |
| `sequential(name)` / `sequential(Owner.class, name)` | Sequential container, unanchored or anchored for hook ids |
| `parallel(name, Pool.named("p"))` | Parallel container; direct children hold one permit each |
| `.before(id)` / `.after(id)` | Lifecycle on the owning task; `after` runs in reverse order |
| `.step(id)` | Body-only child step; `.body(id)` puts a body on the current occurrence |
| `.step(name, op, children -> …)` | Child step with its own sequential subtree |
| `.independent()` / `.dependent()` | Sequential failure continuation (parallel has neither) |
| `.onlyIf(id)` | Runtime condition, resolved hook must return `boolean` |
| `child(repeat(name).maxIterations(n).until(id))` | Bounded replay |
| `.retry(RetryPolicy…)` | Bounded re-attempt |
| `.timeout(Duration)` | Cooperative deadline |
| `.isolated("lock")` | Exclusive logical lock |
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
