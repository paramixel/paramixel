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
| `Sequential.of(name)` | `Sequential.builder(name)` |
| `Parallel.of(name, "pool")` | `Parallel.builder(name, Pool.named("pool"))` |
| `Instance.of(name, Class, factory)` | `bind` for what exists, `before` + `Context.put` for what is created |
| `Scope` | Task lifecycle (`before` / `after`) |
| `Conditional` | `onlyIf(id)` on an attached child group |
| `Loop` | `Repeat.builder(name).maxIterations(n).until(id)` as a `step` |
| `Timeout` | `timeout(Duration)` on any task |
| `Isolated` | `isolated(lockName)` on any task |
| `Retry` / `CleanUp` helpers | `RetryPolicy` / the `after` slot |
| `Context.instance(Class)` | `Context.require(name, Type)` |
| `@Paramixel.Factory` | `@Paramixel.TaskFactory` |
| `Runner.run(Action)` | `Runner.run(Task)` or `Runner.run(TaskBuilder<?>)` |
| Mutable `Descriptor` | Immutable `Result` / `TaskResult` |

## Authoring API (7.0 revision)

If you wrote against an earlier 7.0 alpha, the authoring surface changed: node kinds are types, tree composition is
`step`, and `before`/`after` are single slots.

| Old | New |
| --- | --- |
| `Tasks.sequential(…)` / static import `sequential(…)` | `Sequential.builder(…)` |
| `Tasks.parallel(…)` | `Parallel.builder(…)` |
| `Tasks.repeat(…)` | `Repeat.builder(…)` |
| `Tasks.task(…)` | `Sequential.builder(…)` |
| `.child(x)` | `.step(x)` |
| `.children(list)` | a loop of `.step(x)` |
| two or more `.before(a).before(b)` | `.before(Sequential.builder("<descriptive name>").step(a).step(b))` |
| two or more `.after(a).after(b)` | `.after(Sequential.builder("<descriptive name>").independent().step(b).step(a))` |
| `@Paramixel.Factory` | `@Paramixel.TaskFactory` |
| `run(factory().build())` where the factory returns a builder | `run(factory())` |
| `Task` as a concrete class | `public sealed interface Task permits Sequential, Parallel, Repeat` |

`build()` now returns the concrete node type, so a helper can declare it:

```java
private static SequentialBuilder scenario(final String name) {
    return Sequential.builder(MySuite.class, name).before(ID_START).step(ID_VERIFY).after(ID_STOP);
}
```

### Composing multiple `before` / `after` tasks

A node has exactly one `before` slot and one `after` slot. A second call throws `IllegalStateException` at the call
site rather than silently replacing or stacking.

- Several setup phases become one subtree, in declaration order:
  `.before(Sequential.builder("setup").step(setUpDatabase).step(setUpApplication))`.
- Several cleanup phases become one subtree, keeping the old reverse order and run-all behavior:
  `.after(Sequential.builder("cleanup").independent().step(tearDownApplication).step(tearDownDatabase))`.

A `Sequential` cleanup subtree is `dependent()` by default, so a failing cleanup step skips the later ones.
`independent()` is how a cleanup that must always finish declares itself.

### Bound values versus runtime state

`bind` now means exactly one thing: a value that already exists when the plan is built. It is seeded into the node's
scope before its `before` slot, it may be declared on any node, and it is rendered into report files as a plan
parameter. A value that only exists while the test runs is created in the `before` slot and stored with
`Context.put`; runtime entries are never reported.

```java
Sequential.builder(MySuite.class, "scenario")
        .bind(ENVIRONMENT, environment)
        .before(ID_SET_UP)
        .step(ID_CHECK)
        .after(ID_TEAR_DOWN);

@Paramixel.Id(ID_SET_UP)
public static void setUp(final Context context) throws Exception {
    final var client = Client.start(context.require(ENVIRONMENT, Environment.class));
    context.put(CLIENT, client);
}
```

## Before and after

```java
// 6.x
Instance.of("scenario", Scenario.class, () -> new Scenario(environment))
        .before("setUp()", Scenario.class, Scenario::setUp)
        .body(Sequential.sequential("tests")
                .step(Step.step("check", Scenario.class, Scenario::check)))
        .after("tearDown()", Scenario.class, Scenario::tearDown);

// 7.0
Sequential.builder(MySuite.class, "scenario")
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
needs from the context by name. A `before` or `after` slot may also hold a `Task` or a `TaskBuilder`, in which case it
runs as a subtree that is reported under its owning node.

## Behavior changes to review

1. **`ABORTED` now stops a dependent sequence.** Previously an aborted child did not short-circuit dependent siblings.
   In 7.0 a dependent sequence stops on any non-passing outcome; use `independent()` where continuation is intended.
2. **Skips are visible in aggregation.** A group with passed and skipped children aggregates to `SKIPPED` instead of
   `PASSED`, and `paramixel.failureOnSkip` now defaults to `true`.
3. **`CANCELED` is distinct from `ABORTED`.** A canceled run or timeout-driven stop is `CANCELED`, not an assumption
   abort.
4. **No implicit `AutoCloseable` closing.** Add an `after` slot that closes the fixture.
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
10. **Report files** are generated from `Result`; schema version 2 adds the per-node `parameters` object and a `slot`
    field that marks a `before` or `after` subtree. A `Context.put` value is never reported.

## Configuration

Existing keys keep their names: `paramixel.parallelism`, `paramixel.parallelism.<pool>`, `paramixel.ansi`,
`paramixel.failureOnSkip`, `paramixel.failureOnAbort`, `paramixel.failIfNoTests`, `paramixel.failFast`,
`paramixel.report.file`, `paramixel.match.*`, `paramixel.listener.exclude`. New keys: `paramixel.workers`,
`paramixel.shutdown.graceMillis`, `paramixel.listener.queue.capacity`, `paramixel.listener.drainTimeoutMillis`.

## Migration checklist

- Replace every `org.paramixel.api.action` import.
- Replace `Tasks.*` and the `sequential`/`parallel`/`repeat` static imports with `Sequential.builder`,
  `Parallel.builder`, and `Repeat.builder`.
- Replace `.child(x)` with `.step(x)`, and `.children(list)` with a loop of `.step(x)`.
- Collapse repeated `.before(...)`/`.after(...)` calls into one composed subtree each.
- Rename `@Paramixel.Factory` to `@Paramixel.TaskFactory`, and let the factory return a `TaskBuilder<?>` so a `main`
  method can call `run(factory())`.
- Convert `Instance.of(...)` to a `before` slot that builds the fixture and stores it with `Context.put`, and keep
  `bind` for objects that already exist.
- Review dependent sequences that relied on abort continuation.
- Run the suite and inspect `result.root()` for newly visible `SKIPPED`/`CANCELED` outcomes.
