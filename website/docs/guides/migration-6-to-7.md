---
title: Migrating from 6.x to 7.0
description: Breaking changes in Paramixel 7.0.0 and how to update.
---

# Migrating from 6.x to 7.0

7.0.0 is a breaking release. There are no compatibility shims: removed APIs are gone and a removed or renamed
configuration key simply has no effect. Every removal below has one replacement.

## One entry point per intent: `Runner`

| Before (6.x) | Now (7.0) |
| --- | --- |
| `Runner.defaultRunner()` | `Runner.builder().build()` |
| `run(Selector)` returned `Optional<Result>` | `run(Selector)` returns `Result` (with an empty descriptor when nothing is found) |
| `runAndReturnExitCode(Action)` / `(Selector)` / `()` | `run(...).exitCode()` |
| `runAndExit(Action)` / `(Selector)` | `System.exit(run(...).exitCode())` |
| `Runner.main(String[])` | `Launcher.main(String[])` |

Also:

- `run(Action)` does **no** discovery and **no** hook scan: the tree is the complete definition. Wrap it in a
  `Scope` when it needs setup and teardown. `@BeforeAll`/`@AfterAll` hooks run only in discovery runs (`run()`,
  `run(Selector)`) and are global there.
- `run(Selector)` uses exactly the given selector. A `paramixel.match.*` key is no longer merged into it; the runner
  reports `Listener.onWarning` when such a key is set and ignored. Use
  `Selector.configured().and(mine)` to combine them.

## Typed fixtures: `Instance.of(...)` replaces `withInstance`

```java
// 6.x
instance(env.name(), () -> new MetricsTest(env))
        .body(scope("scenario")
                .before(step("setUp()", withInstance(MetricsTest.class, MetricsTest::setUp)))
                .body(sequential("tests")
                        .child(step("testHealthy()", withInstance(MetricsTest.class, MetricsTest::testHealthy))))
                .after(step("tearDown()", withInstance(MetricsTest.class, MetricsTest::tearDown))));

// 7.0
of(env.name(), MetricsTest.class, () -> new MetricsTest(env))
        .before("setUp()", MetricsTest::setUp)
        .body(sequential("tests")
                .child(step("testHealthy()", MetricsTest.class, MetricsTest::testHealthy)))
        .after("tearDown()", MetricsTest::tearDown);
```

- The fixture type appears once, in `of(...)`; `before`, `after` and every typed step name only the method.
- `Context.withInstance` is removed; `Context.instance`/`requireInstance` remain for untyped steps.
- `Context` is read-only: there is no public `bind`. Fixtures come only from an `Instance` in the tree, which is
  what lets the compile step reject a typed step whose fixture is missing (or ambiguous) before any user code runs.
- The factory runs lazily, inside the node's own `before` step, on the thread executing that node. A node that is
  skipped, aborted or cancelled before it starts never calls it and does not run `after`.
- `AnnotationResolver` and `@Paramixel.Id` are removed: method references do the same job with compile-time
  safety. Delete the annotation from the fixtures and use typed steps.

## Iteration: `each(...)` replaces `Each`

| Before (6.x) | Now (7.0) |
| --- | --- |
| `Each.sequential("x", items, mapper)` | `sequential("x").each(items, mapper)` |
| `Each.parallel("x", "pool", items, mapper)` | `parallel("x", "pool").each(items, mapper)` |
| `Each.*` stream overloads | materialize the stream: `items.stream().toList()` |

`each` mixes with fixed `child(...)` calls, and the mapper runs eagerly at define time. `children(Iterable)` adds
already built children.

## Concurrency is configured with named pools

Every `Parallel` names a pool, and the pool's size is configuration:

```java
parallel("environments", "docker").each(items, item -> ...);
```

```properties
paramixel.parallelism.docker=4
```

| Before (6.x) | Now (7.0) |
| --- | --- |
| `paramixel.parallelism=N` bounded all executing work | `paramixel.parallelism=N` sizes only the runner's **root pool**, used by discovery |
| `parallel("x").parallelism(3)` | `parallel("x", "pool")` with `paramixel.parallelism.pool=3` |
| `Parallel.ROOT_POOL` | removed; a pool may be named `root` like any other |
| `Parallel.parallelism()` | `Parallel.pool()` |

Rules the runner enforces **before anything runs**:

- A pool a `Parallel` names must be defined, otherwise the run fails and the message names the key to add. Every
  undefined pool is reported in one message.
- A pool is shared by name across the whole tree: at most that many children of all `Parallel` nodes using it are
  alive at once. Name pools for what they limit (`docker`, `environments`), not for where they sit.
- A pool cannot be nested under a `Parallel` that uses the same pool.
- Pools and `Isolated` locks cannot be taken in conflicting order in different branches.
- A pool size is a positive integer, or `@cores`, `@cores*N` or `@cores/N` (never below 1).
- Pool names start with a letter and contain only letters, digits, `_` and `-`; use `Configuration.poolKey(name)`.
- A `paramixel.parallelism.<name>` key that names a pool no `Parallel` uses is reported through
  `Listener.onWarning`, which catches a typo in a properties file.

A program that calls `Runner.run(action)` directly has no discovered root `Parallel`, so `paramixel.parallelism`
does not apply to it; its own top-level `Parallel` names a pool.

## Selectors

| Before (6.x) | Now (7.0) |
| --- | --- |
| `Selector.packageTreeOf(X.class)` | `Selector.builder().packageTreeOf(X.class).build()` |
| `Selector.classOf(X.class)` | `Selector.builder().classOf(X.class).build()` |
| `Selector.packageOf(X.class)` | `Selector.builder().packagePattern(Pattern.compile(Pattern.quote(X.class.getPackageName()))).build()` |
| `Selector.packageRegex(s)`, `classRegex(s)`, `tagRegex(s)` | `packagePattern`, `classPattern`, `tagPattern` with a `Pattern` |
| `Selector.all()` | `Selector.builder().build()` |
| `Selector.not(x)` | the `exclude…` twin of the same dimension |
| `Selector.or(a, b)` | one pattern that matches either alternative |
| `Selector.and(a, b)` | `a.and(b)` |

`not(packageRegex(...))` also *matched nothing* in 6.x, so an exclusion is both the replacement and the fix.
Dimensions are ANDed, an empty builder selects everything, an untagged class never matches a tag include, and an
untagged class is kept by a tag exclude.

## Status and result values

- `Status` has a `Kind` (`PENDING`, `RUNNING`, `PASSED`, `FAILED`, `SKIPPED`, `ABORTED`) and optional message and
  throwable. Compare with `status().kind()`, not with `Status.PASSED`: `Status.failed("x").equals(Status.FAILED)`
  is `false`.
- `Status` equality compares kind and message; the throwable is ignored.
- `Result.isPassed()`/`isFailed()`/`isSkipped()`/`isAborted()`, `Descriptor.is*()`, `Descriptor.message()` and
  `Descriptor.throwable()` are removed. Use `status().kind()` and `status().message()`.
- `Result.exitCode()` is the single exit-code authority; `result.status()` is the **root** status after promotion,
  not the most severe status in the tree.
- `Descriptor` gains `displayName()`, `path()` and `duration()`, and `Context` gains `descriptor()`.

## Backoff, Loop, Retry, CleanUp

| Before (6.x) | Now (7.0) |
| --- | --- |
| `Loop.DelayPolicy.Linear` / `.Exponential(base)` | `Backoff.fixed(d)`, `Backoff.linear(step)`, `Backoff.exponential(base, max)` |
| `Loop.Builder.delay(Duration)` | `Loop.Builder.delay(Backoff.fixed(duration))` |
| `Retry.Policy.fixed(a, b)` / `.exponential(a, b)` | `Retry.of(Backoff.linear(a), b)` / `Retry.of(Backoff.exponential(a, b), b)` |
| `PolicyException` | removed: backoff arithmetic saturates and never throws |
| `Retry.reset()`, `clear()`, `hasRun()` | removed: `Retry` is immutable and `run` may be called repeatedly |
| `Retry.Result.isSuccessful()` | `Retry.Result.successful()` (it is a record) |
| `Loop.Builder.until(Predicate<Context>)` | still available, plus `until(BiPredicate<Context, Status>)` and `untilPassed()` |
| `CleanUp.throwable()` returning `null` | returns `Optional<Throwable>`; `isEmpty`/`isNotEmpty` are removed |

## Removed types and methods

`Assert`, `Delay`, `Each`, `AnnotationResolver`, `@Paramixel.Id`, `CycleDetectedException`, `PolicyException`,
`Loop.DelayPolicy`, `Retry.Policy`, the seven `Selector` sub-interfaces, `Context.withInstance`,
`Configuration.getFloat`/`getDouble`, `Parallel.ROOT_POOL`, `Sequential.isIndependent()`,
`Listener.initialize` (merged into `onRunStarted(Configuration)`), and `Runner.main`.

Replacements: `Assert` → a `step` that throws `fail(...)`; `Delay` → `step("wait", ctx -> Thread.sleep(ms))`;
`Configuration.getFloat`/`getDouble` → `get(key, Double::valueOf)`.

## Listener

- `initialize(Configuration)` is merged into `onRunStarted(Configuration)`, which is the first callback of a run.
- `onDiscoveryCompleted(Descriptor)` now takes `Optional<Descriptor>`.
- `onBeforeExecution` and `onAfterExecution` are invoked **concurrently** from pool worker threads; implement them
  thread-safely.
- `onWarning` reports ignored `paramixel.match.*` keys, unknown `paramixel.*` keys, and unused pool keys.

## Build and configuration

- An unknown key that starts with `paramixel.` is reported through `Listener.onWarning` instead of being ignored.
- `Configuration` documents every key and its default in one table.
- The project version is `7.0.0-ALPHA-1`; releases use `x.y.z` or `x.y.z-ALPHA-<n>`.
