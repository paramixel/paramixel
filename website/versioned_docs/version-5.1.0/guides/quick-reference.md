---
title: Quick Reference
description: Single-page reference for Paramixel types, annotations, configuration, and exceptions.
---

# Quick Reference

## Built-in action types

| Type | Kind | Factory method | Children | Before/After | Category |
| --- | --- | --- | --- | --- | --- |
| `Step` | `"Step"` | `Step.of(name, consumer)` | No | No | Leaf |
| `Lifecycle` | `"Lifecycle"` | `Lifecycle.of(name)` | Yes | Yes (optional) | Composite |
| `Sequential` | `"Sequential"` | `Sequential.of(name)` | Yes | No | Composite |
| `Parallel` | `"Parallel"` | `Parallel.of(name)` | Yes | No | Composite |
| `Instance` | `"Instance"` | `Instance.of(name, supplier)` | Yes | Yes (auto) | Composite |
| `Static` | `"Static"` | `Static.of(name)` | Yes | Yes (optional) | Composite |
| `Repeat` | `"Repeat"` | `Repeat.of(name)` | Yes (single) | No | Composite |
| `Timeout` | `"Timeout"` | `Timeout.of(name)` | Yes (single) | No | Composite |
| `Delay` | `"Delay"` | `Delay.of(name, duration)` | No | No | Leaf |
| `AssertTrue` | `"AssertTrue"` | `AssertTrue.of(name, condition)` | No | No | Leaf |
| `AssertFalse` | `"AssertFalse"` | `AssertFalse.of(name, condition)` | No | No | Leaf |

## Annotations

All annotations are inner types of `org.paramixel.api.Paramixel`.

| Annotation | Target | Attribute | Default | Purpose |
| --- | --- | --- | --- | --- |
| `@Factory` | `METHOD` | — | — | Marks method as action factory for classpath discovery |
| `@Priority` | `TYPE` | `int value()` | `0` | Discovery ordering (higher = earlier) |
| `@Disabled` | `METHOD` | `String value()` | `""` | Excludes factory from discovery |
| `@Tag` | `METHOD` | `String value()` | (required) | Logical tag for selector filtering (repeatable) |
| `@Tags` | `METHOD` | `Tag[] value()` | (required) | Container for multiple `@Tag` |
| `@Id` | `METHOD` | `String value()` | (required) | Stable identifier for `AnnotationResolver` lookup |

## Configuration keys

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `paramixel.parallelism` | int | `availableProcessors()` | Default scheduler parallelism |
| `paramixel.scheduler.queue.capacity` | int | `1024` | Maximum scheduler-ready tasks and permit waiters |
| `paramixel.ansi` | String | `"auto"` | ANSI output: `"true"`, `"false"`, or `"auto"` |
| `paramixel.failureOnSkip` | boolean | `false` | Promote `SKIPPED` to failing exit code |
| `paramixel.failureOnAbort` | boolean | `true` | Promote `ABORTED` to failing exit code |
| `paramixel.failIfNoTests` | boolean | `false` | Fail when no action factories are discovered |
| `paramixel.listener.exclude` | String | (none) | Comma-separated tokens to suppress listener output sections |
| `paramixel.report.file` | String | (none) | Path to per-run summary report |
| `paramixel.match.package.regex` | String | (none) | Regex for filtering by package name |
| `paramixel.match.class.regex` | String | (none) | Regex for filtering by class name |
| `paramixel.match.tag.regex` | String | (none) | Regex for filtering by `@Paramixel.Tag` value |

`paramixel.strictThreadLifecycle` is a Maven plugin parameter only (not a core `Configuration` key). See [Maven Plugin](../integrations/maven-plugin).

## Exceptions

### Author-initiated exceptions

| Exception | Package | Static factory | Outcome status |
| --- | --- | --- | --- |
| `FailException` | `org.paramixel.api.exception` | `FailException.fail()` / `fail(String)` | `FAILED` |
| `SkipException` | `org.paramixel.api.exception` | `SkipException.skip()` / `skip(String)` | `SKIPPED` |
| `AbortedException` | `org.paramixel.api.exception` | `AbortedException.abort()` / `abort(String)` | `ABORTED` |

### Framework exceptions

| Exception | Package | When thrown |
| --- | --- | --- |
| `ConfigurationException` | `org.paramixel.api.exception` | Invalid configuration value |
| `CycleDetectedException` | `org.paramixel.api.exception` | Cyclic parent-child in action tree |
| `ResolverException` | `org.paramixel.api.exception` | Classpath scanning or discovery failure |
| `PolicyException` | `org.paramixel.api.exception` | Retry policy computation failure |

## Selector factory methods

| Method | Description |
| --- | --- |
| `Selector.all()` | Matches everything |
| `Selector.packageRegex(String)` | Package name regex (`find()` semantics) |
| `Selector.classRegex(String)` | Fully qualified class name regex (`find()` semantics) |
| `Selector.tagRegex(String)` | `@Paramixel.Tag` value regex (`find()` semantics) |
| `Selector.packageTreeOf(Class<?>)` | Package + all subpackages (anchored) |
| `Selector.packageOf(Class<?>)` | Exact package match (anchored) |
| `Selector.classOf(Class<?>)` | Exact class name match (anchored) |
| `Selector.and(Selector...)` | Logical AND |
| `Selector.or(Selector...)` | Logical OR |
| `Selector.not(Selector)` | Logical NOT |

## Runner methods

| Method | Returns | Description |
| --- | --- | --- |
| `Runner.defaultRunner()` | `Runner` | Creates with default config + listener |
| `Runner.builder()` | `Runner.Builder` | Fluent builder |
| `run(Spec<?>)` | `Result` | Execute a pre-built action tree |
| `run(Selector)` | `Optional<Result>` | Discover + execute by selector |
| `runAndReturnExitCode(Spec<?>)` | `int` | Execute; returns `0` or `1` |
| `runAndReturnExitCode(Selector)` | `int` | Discover + execute; returns `0` or `1` |
| `runAndExit(Spec<?>)` | `void` | Execute + `System.exit` |
| `runAndExit(Selector)` | `void` | Discover + `System.exit` |
| `run()` | `int` | Classpath discovery + execute; returns exit code |
| `Runner.main(String[])` | `void` | CLI entry; calls `System.exit(run())` |

## Status aggregation order

`FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`

## Exit code mapping

| Effective status | Exit code |
| --- | --- |
| `PASSED` | `0` |
| `SKIPPED` | `0` (or `1` when `failureOnSkip=true`) |
| `ABORTED` | `1` (default; `0` when `failureOnAbort=false`) |
| `FAILED` / `PENDING` | `1` |

## Report format by extension

| Extension | Format |
| --- | --- |
| `.json` | JSON |
| `.xml` | XML |
| `.html` / `.htm` | HTML |
| `.log`, `.txt`, other | Plain text |

## Listener callbacks

| Callback | When |
| --- | --- |
| `initialize(Configuration)` | Before the run begins |
| `onRunStarted()` | Before discovery |
| `onDiscoveryStarted()` | Before the discovery phase |
| `onDiscoveryCompleted(Descriptor)` | After descriptor tree is built |
| `onBeforeExecution(Descriptor)` | Before each action executes |
| `onAfterExecution(Descriptor)` | After each action reaches terminal status |
| `onRunCompleted(Result)` | After the run completes |
