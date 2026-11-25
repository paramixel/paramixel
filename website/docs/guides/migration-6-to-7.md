---
title: Migrating from 6.x to 7.0
description: The action API was replaced by one immutable Task definition.
---

# Migrating from 6.x to 7.0

Paramixel 7 is a deliberate greenfield refactor. There is no compatibility layer: the old `org.paramixel.api.action`
package is gone, and keeping two parallel engines would have made behavior ambiguous. This guide maps each concept and
names the behavior changes that can affect an existing suite.

## Concept mapping

| 6.x | 7.0 |
| --- | --- |
| `Action` / `ActionBuilder` | `Task` (definition) and `TaskBuilder` (authoring) |
| `Step` | A child task with a body |
| `Sequential` | A sequential task |
| `Parallel(displayName, "pool")` | `parallel(displayName, Pool.named("pool"))` |
| `Instance.of(name, Class, factory)` | `bind`/`put` + `require` in the per-scope context |
| `Scope` | Task lifecycle (`before` / `after`) |
| `Conditional` | `onlyIf(id)` on an attached child group |
| `Loop` | `child(repeat(name).maxIterations(n).until(id))` |
| `Timeout` | `timeout(Duration)` on any task |
| `Isolated` | `isolated(lockName)` on any task |
| `Retry` / `CleanUp` helpers | `RetryPolicy` / `after` callbacks |
| `Context.instance(Class)` | `Context.require(name, Type)` |
| `Runner.run(Action)` | `Runner.run(Task)` |
| Mutable `Descriptor` | Immutable `Result` / `TaskResult` |

## Before and after

```java
// 6.x
Instance.of("scenario", Scenario.class, () -> new Scenario(environment))
        .before("setUp()", Scenario.class, Scenario::setUp)
        .body(Sequential.sequential("tests")
                .child(Step.step("check", Scenario.class, Scenario::check)))
        .after("tearDown()", Scenario.class, Scenario::tearDown);

// 7.0
sequential(MySuite.class, "scenario")
        .bind(ENVIRONMENT, environment)
        .before(ID_SET_UP)
        .step(ID_CHECK)
        .after(ID_TEAR_DOWN);

@Paramixel.Id(ID_SET_UP)
public static void setUp(final Context context) {
    context.put(FIXTURE, new Scenario(context.require(ENVIRONMENT, Environment.class)));
}
```

The class literal appears once per definition root; the hooks are ordinary static methods, and each one reads what it
needs from the context by name.

## Behavior changes to review

1. **`ABORTED` now stops a dependent sequence.** Previously an aborted child did not short-circuit dependent siblings.
   In 7.0 a dependent sequence stops on any non-passing outcome; use `independent()` where continuation is intended.
2. **Skips are visible in aggregation.** A group with passed and skipped children aggregates to `SKIPPED` instead of
   `PASSED`, and `paramixel.failureOnSkip` now defaults to `true`.
3. **`CANCELED` is distinct from `ABORTED`.** A canceled run or timeout-driven stop is `CANCELED`, not an assumption
   abort.
4. **No implicit `AutoCloseable` closing.** Add an `after` callback that closes the fixture.
5. **No reflective fixture lookup, and no typed callbacks.** `Context.instance(Class)` is gone, and so are the callback
   overloads that replaced it: hooks are identified by `@Paramixel.Id` and read state from the context with
   `require(name, Type)`.
6. **Pools are required and validated.** An undefined pool fails before execution, same-pool nesting is rejected, and
   the discovery root uses a `discovery` pool whose capacity defaults to `paramixel.parallelism`.
7. **Timeouts are cooperative and honest.** A callback that ignores interruption yields
   `result.isQuiescent() == false`, withheld cleanup, and a non-zero exit code.
8. **`failIfNoTests` defaults to `true`** for CI safety.
9. **Listener API changed.** `Descriptor` callbacks were replaced by `TaskEvent` and the immutable `Result`. Listener
   exceptions are isolated and appear in `result.diagnostics()`.
10. **Report files** are generated from `Result`; the formats and extension-based selection are unchanged.

## Configuration

Existing keys keep their names: `paramixel.parallelism`, `paramixel.parallelism.<pool>`, `paramixel.ansi`,
`paramixel.failureOnSkip`, `paramixel.failureOnAbort`, `paramixel.failIfNoTests`, `paramixel.failFast`,
`paramixel.report.file`, `paramixel.match.*`, `paramixel.listener.exclude`. New keys: `paramixel.workers`,
`paramixel.shutdown.graceMillis`, `paramixel.listener.queue.capacity`, `paramixel.listener.drainTimeoutMillis`.

## Migration checklist

- Replace every `org.paramixel.api.action` import.
- Convert `Instance.of(...)` to a `before` hook that builds the fixture and stores it with `Context.put`.
- Convert `Parallel.parallel("x", "pool")` to `parallel(Owner.class, "x", Pool.named("pool"))`.
- Replace `Loop` with `child(repeat(...))`, and the `Retry` helper with `RetryPolicy`.
- Replace `Context.instance(Class)` lookups with `Context.require(name, Type)`.
- Convert every lifecycle callback and step body to a `public static` method annotated with `@Paramixel.Id`, and make the
  definition root name its owning class.
- Review dependent sequences that relied on abort continuation.
- Run the suite and inspect `result.root()` for newly visible `SKIPPED`/`CANCELED` outcomes.
