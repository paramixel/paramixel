---
title: Discovery
description: How annotated factories become a validated root definition.
---

# Discovery

Discovery validates the matched provider declarations, invokes them to produce definitions, and compiles the composed
plan before executing task callbacks.

## Annotations

| Annotation | Purpose |
| --- | --- |
| `@Paramixel.TaskFactory` | `public static`, no parameters or one `Configuration`, returns `Task` or a builder |
| `@Paramixel.BeforeAll` | Runs before the discovered factories; gates them |
| `@Paramixel.AfterAll` | Runs after the started run, even when a factory failed |
| `@Paramixel.Disabled` | Excludes a factory or run hook |
| `@Paramixel.Tag` | Selection and reporting metadata |
| `@Paramixel.Priority` | Discovery and run-hook ordering for a class (declared on the type) |

```java
@Paramixel.TaskFactory
public static TaskBuilder<?> factory() {
    final var root = Parallel.builder(MySuite.class, "Environments", Pool.named("environments"));
    for (final var environment : environments) {
        root.step(Sequential.builder(environment.name()).bind(ENVIRONMENT, environment).step(ID_VERIFY));
    }
    return root;
}
```

A factory may return a built `Task` or a `TaskBuilder<?>`; the framework builds a builder. A factory returning `null` is
an explicit skip with a recorded reason. If every selected factory returns `null`,
`paramixel.failIfNoTests` decides the exit code. Any other invalid shape — not `static`, parameters other than the sole
`Configuration`, or an unrelated return type — fails the run with `ResolverException` **before** any discovered test code
runs.

Definition validation failures reported as `CompilationException` retain their diagnostics in either return form.

## Configuration-driven structure

A factory may declare one `Configuration` parameter to receive the run's effective configuration. That is the same object
`Context.configuration()` exposes, and it has already been validated before the factory is invoked, so the factory can
decide what to author rather than only what the authored tasks do:

```java
@Paramixel.TaskFactory
public static TaskBuilder<?> factory(final Configuration configuration) {
    final var environments = configuration
            .getString("example.environments")
            .map(value -> value.split(","))
            .orElse(new String[] {"postgres"});
    final var root = Parallel.builder(MySuite.class, "Environments", Pool.named("environments"));
    for (final var environment : environments) {
        root.step(Sequential.builder(environment.trim()).step(ID_VERIFY));
    }
    return root;
}
```

- The parameter is optional; a no-parameter factory is unchanged, and the return type is independent of it, so all four
  combinations of `Task` or `TaskBuilder` with or without the parameter are accepted.
- The type must be exactly `org.paramixel.api.Configuration`; any other parameter count or type fails discovery.
- Combining the parameter with a `null` return lets a factory opt out of contributing a definition at all.
- `@BeforeAll` and `@AfterAll` methods take no parameters.

## Composed structure

```
Paramixel (independent sequential root)
  run (dependent sequential)
    BeforeAll hooks (when any)
    tasks (parallel, pool "discovery")    ← discovered factories
  AfterAll hooks (when any)
```

- A failing or skipping `BeforeAll` hook skips the factories.
- `AfterAll` hooks still run after a started run, in reverse order. A failing hook does not prevent the remaining
  `AfterAll` hooks from running.
- `tasks` is the fail-fast scope: with `paramixel.failFast=true`, a failed factory (`FAILED`, including a timeout)
  stops admitting later ones. A skipped or aborted factory does not.
- Capacity of the `discovery` pool defaults to `paramixel.parallelism`; override it with
  `Runner.Builder.pool(Pool.named("discovery"), n)` or `paramixel.parallelism.discovery`.

## Direct execution versus discovery

`Runner.run(Task)` executes exactly the supplied definition: no classpath scan, no hooks, no selection.
`Runner.run(TaskBuilder<?>)` builds the definition first and then behaves identically. Discovery happens only through
`Runner.run()` and `Runner.run(Selector)`.

## Selection

`Selector` filters by package tree, class, and tag patterns. The selector passed to `Runner.run(Selector)` is used
exactly as given; if it ignores configured `paramixel.match.*` keys, the runner warns instead of merging them silently.
When nothing matches, the run reports `SKIPPED` and `paramixel.failIfNoTests` (default `true`) decides the exit code.

Tag selection reads the factory's annotations. On its discovered root occurrence, Paramixel appends missing
annotation tags after builder tags. The merged list appears in `Context.tags()` and `TaskResult.tags()`.

Standalone `@BeforeAll` and `@AfterAll` methods are run-wide. A method carrying both `@TaskFactory` and a run-hook
annotation is rejected when selected as a factory and is otherwise ignored.
