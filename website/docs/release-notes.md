---
title: Release Notes
description: Current documentation-facing release notes.
---

# Release Notes

## 7.0.0-ALPHA-1

7.0.0 is a breaking release: it reduces the public API to one way to do each thing, fixes the confirmed defects, and
keeps the execution model (define → validate → compile → execute) and the named-pool concept unchanged. There are no
compatibility shims. See the [6.x to 7.0 migration guide](guides/migration-6-to-7.md) for the mechanical changes.

**Execution and runner**

- **One entry point per intent.** `Runner.run(Action)`, `run(Selector)` and `run()` all return a `Result`;
  `Result.exitCode()` is the single exit-code authority. `runAndExit`, `runAndReturnExitCode`, `defaultRunner()` and
  `Runner.main` are removed, and `Launcher` holds `main`.
- **`run(Action)` does no discovery.** A direct tree is the complete definition: no classpath scan and no hooks. Setup
  and teardown around a tree is a `Scope`. In discovery runs hooks stay global.
- **`run(Selector)` uses exactly the selector given.** A `paramixel.match.*` key it ignores is reported through
  `Listener.onWarning` instead of being merged silently.

**Typed fixtures**

- **`Instance.of(name, type, factory)` is a typed fixture builder** with `before(name, consumer)`,
  `body(...)` and `after(name, consumer)`, and `Step.step(name, type, consumer)` runs a method on it. The fixture type
  appears once instead of on every step, and `Context.withInstance` is removed.
- **Creation is lazy and node-local.** The factory runs inside the node's own before step; a node that is skipped,
  aborted or cancelled never calls it and does not run `after`.
- **A typed step whose fixture is missing or ambiguous is rejected before any user code runs**, naming the step.
- **`AnnotationResolver` and `@Paramixel.Id` are removed**: method references do the same job with compile-time
  safety.

**Iteration, selectors, values**

- **`each(items, mapper)` on `Sequential.Builder` and `Parallel.Builder` replaces `Each`**, and
  `children(Iterable)` adds already built children.
- **`Selector.builder()`** offers `packageTreeOf`, `classOf`, `packagePattern`, `classPattern`, `tagPattern` and an
  `exclude…` twin of each; dimensions are ANDed. `Selector.not(...)` of a single-dimension selector used to match
  nothing; an exclusion is the replacement and the fix. `Selector.configured()` is the explicit configuration
  selector.
- **`Status` is a value**: a `Kind` (`PENDING`, `RUNNING`, `PASSED`, `FAILED`, `SKIPPED`, `ABORTED`) plus optional
  message and throwable, with `equals` on kind and message. `isX()`, `message()` and `throwable()` are removed from
  `Result` and `Descriptor`; `Descriptor` gains `displayName()`, `path()` and `duration()`.
- **`Backoff`** (`fixed`, `linear`, `exponential(base, max)`, saturating and never throwing) replaces
  `Loop.DelayPolicy`, `Retry.Policy` and `PolicyException`. `Loop` gains `until(BiPredicate<Context, Status>)` and
  `untilPassed()`, and unreached iterations are reported "loop satisfied". `Retry` is immutable and re-entrant with a
  `Result` record. `CleanUp.throwable()` returns `Optional`.
- **`Conditional`** reports `"condition not met: <name>"` on the skipped body instead of a silent skip.

**Concurrency and reporting**

- **Named pools.** Every `Parallel` names a pool sized by `paramixel.parallelism.<name>`; `paramixel.parallelism`
  sizes only the runner's root pool. The root pool can no longer be aliased by a user pool, so a pool may be called
  `root`. Pool sizes may be `@cores`, `@cores*N` or `@cores/N`, resolved against the processors the JVM reports
  (minimum 1).
- **Validation reports every problem at once** as a `ConfigurationException`: an undefined pool, a pool nested under
  itself, and a conflicting pool/lock order all fail before anything runs.
- **The summary prints pool sizes and peak usage**, and a `paramixel.parallelism.<name>` key that names a pool no
  `Parallel` uses is reported through `Listener.onWarning`.
- **`Listener.initialize` is merged into `onRunStarted(Configuration)`**, `onDiscoveryCompleted` takes
  `Optional<Descriptor>`, and `onBeforeExecution`/`onAfterExecution` document their concurrent invocation.

**Removed**

`Assert`, `Delay`, `Each`, `AnnotationResolver`, `@Paramixel.Id`, `CycleDetectedException`, `PolicyException`,
`Loop.DelayPolicy`, `Retry.Policy`, the seven `Selector` sub-interfaces, `Context.withInstance`,
`Configuration.getFloat`/`getDouble`, `Parallel.ROOT_POOL`, `Sequential.isIndependent()`, and the previously
removed `Static`, `Sequence`, `Repeat` and `Until`.

## Unreleased (carried from the scheduler change)

- **Scheduler replaced by a depth-first execution engine** — Actions are compiled into a per-run execution plan
  before execution starts, and the plan is executed by recursive depth-first descent with a hard concurrency budget;
  there is no work-stealing or ready queue. Concurrency is limited with named pools.
- **`paramixel.scheduler.queue.capacity` removed** — The scheduler ready queue no longer exists, so the property has
  no effect. Delete it from build scripts and `paramixel.properties` files.

## 6.2.1

- **Bug fix: regex selector matching** — `Selector.packageRegex()`, `Selector.classRegex()`, `Selector.tagRegex()`,
  and the `paramixel.match.*.regex` configuration keys incorrectly used `Pattern.find()` (substring match) instead of
  `Pattern.matches()` (full-string match). The `match` in the configuration key names specifies the intended contract;
  the implementation did not honor it. This is now fixed. Patterns that previously relied on the incorrect substring
  behavior must be updated with wildcards (e.g., `.*smoke.*`). See
  [Migration 6.2.x to 6.2.1](./guides/migration-6.2-to-6.2.1) for details.

## 6.0.0 documentation baseline

These docs reflect the current source tree:

- Java 17+ core API under `org.paramixel.api`.
- Action trees are composable execution units executed by `Runner`.
- Configuration uses `paramixel.properties`, JVM system properties, and programmatic `Configuration` objects.
- Maven plugin artifact is `org.paramixel:maven-plugin` with goal prefix `paramixel` and goal `test`.
- Gradle usage is via a `JavaExec` task that runs `org.paramixel.api.Runner`.
- Reports are enabled with `paramixel.report.file`.

Migration guides are available for upgrading from 1.x, 2.x, 3.x, 4.x, 5.0.x, and 5.1.x directly to 6.0.0.
