---
title: Discovery
description: How annotated factories become a validated root definition.
---

# Discovery

Discovery turns annotated static factories into one root definition, validates every matched method, and only then
executes.

## Annotations

| Annotation | Purpose |
| --- | --- |
| `@Paramixel.Factory` | `public static`, no parameters, returns `Task` or a builder |
| `@Paramixel.BeforeAll` | Runs before the discovered factories; gates them |
| `@Paramixel.AfterAll` | Runs after the started run, even when a factory failed |
| `@Paramixel.Disabled` | Excludes a factory |
| `@Paramixel.Tag` | Selection and reporting metadata |
| `@Paramixel.Priority` | Deterministic discovery ordering |

```java
@Paramixel.Factory
public static Task factory() {
    return parallel("Environments", Pool.named("environments"))
            .each(environments, env -> forInstance(env.name(), () -> new Probe(env))
                    .step("verify", Probe::verify))
            .build();
}
```

A factory returning `null` is an explicit skip with a recorded reason. Any other invalid shape — not `static`, has
parameters, or returns an unrelated type — fails the run with `ResolverException` **before** any discovered test code
runs.

## Composed structure

```
Paramixel (independent sequential root)
  run (dependent sequential)
    BeforeAll hooks (when any)
    actions (parallel, pool "discovery")   ← discovered factories
  AfterAll hooks (when any)
```

- A failing `BeforeAll` hook skips the factories.
- `AfterAll` hooks still run after a started run.
- `actions` is the fail-fast scope: with `paramixel.failFast=true`, a non-passing factory stops admitting later ones.
- Capacity of the `discovery` pool defaults to `paramixel.parallelism`; override it with
  `Runner.Builder.pool(Pool.named("discovery"), n)` or `paramixel.parallelism.discovery`.

## Direct execution versus discovery

`Runner.run(Task)` executes exactly the supplied definition: no classpath scan, no hooks, no selection. Discovery happens
only through `Runner.run()` and `Runner.run(Selector)`.

## Selection

`Selector` filters by package tree, class, and tag patterns. The selector passed to `Runner.run(Selector)` is used
exactly as given; if it ignores configured `paramixel.match.*` keys, the runner warns instead of merging them silently.
When nothing matches, the run reports `SKIPPED` and `paramixel.failIfNoTests` (default `true`) decides the exit code.
