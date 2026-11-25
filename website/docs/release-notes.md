---
title: Release Notes
description: Notable changes by release.
---

# Release Notes

## 7.0.0

Greenfield refactor. The public action hierarchy was replaced by one immutable `Task` definition.

**Highlights**

- `Task` / `TaskBuilder` / `Tasks` replace `Action`, `ActionBuilder`, and the per-shape classes.
- Fixtures live in the per-scope context: `bind(name, value)` seeds it, `put`/`require`/`find`/`remove` manage it,
  and `after` hooks release it. The framework never closes a stored value and never probes for `AutoCloseable`.
- `parallel(name, Pool.named("..."))` replaces raw string pool identifiers; `Pool` is a value object.
- `sequential` / `parallel` describe concurrency; `dependent()` / `independent()` describe only failure continuation and
  exist on sequential builders only.
- `repeat` (bounded replay) and `RetryPolicy` (bounded re-attempt) are distinct, typed policies.
- `when` / `onlyIf` conditions, `timeout`, `isolated`, typed `Key` resources, `tags`, and reproducible `shuffle` are
  attributes of any task.
- A pure compiler validates the plan and aggregates diagnostics before any user code runs.
- A single-threaded scheduler with a worker pool replaces the previous engine: deterministic depth-first admission,
  non-blocking waits, explicit permit lifetimes, and deadlock protection.
- Timeouts are cooperative and reported honestly: a callback that ignores interruption produces a non-quiescent result
  with withheld cleanup and a non-zero exit code.
- `CANCELED` is distinct from `ABORTED`, and dependent sequences now stop on any non-passing outcome.
- Immutable `Result` / `TaskResult` / `PhaseResult` / `Failure` replace mutable descriptors; listeners receive bounded,
  ordered `TaskEvent`s.
- `paramixel.failIfNoTests` and `paramixel.failureOnSkip` now default to `true`. An empty discovery's exit code follows
  `paramixel.failIfNoTests` alone.
- `paramixel.failFast` triggers only on a `FAILED` root task (timeouts included); deliberate skips, false conditions,
  and assumption aborts do not stop later roots.
- The Maven plugin honors `paramixel.properties` and `<properties>` values (including `paramixel.match.*`) for any
  parameter that is not explicitly set.
- A timeout or run cancellation runs the `after` callbacks and resource releases of every started descendant, even when
  the subtree is idle (for example during a retry backoff). A canceled retry or repeat delay ends at once and never
  starts the next attempt.
- XML and HTML reports replace characters that XML 1.0 forbids (such as ANSI escape codes in failure messages) with
  U+FFFD, and an unpaired surrogate in a message no longer prevents a report file from being written.
- A `@Paramixel.Factory` or hook method that returns `null` is reported as a warning naming the method, and the warning
  is kept in the run diagnostics.
- An invalid `paramixel.workers`, `paramixel.shutdown.graceMillis`, or `paramixel.parallelism` fails a discovery run
  with `ConfigurationException` before any factory is invoked. A non-positive `paramixel.parallelism` is rejected
  instead of being raised to one.
- A false condition's skip reason belongs to its own attempt: a retried task that later passes or fails no longer
  reports a stale `condition was false` reason.

**Breaking changes**

- The `org.paramixel.api.action` package and the mutable `Descriptor` tree are removed.
- `Context.instance(Class)` is removed; typed callbacks replaced it, and this release removes those too in favour of
  identifier-bound hooks that read the context by name.
- Implicit closing of `AutoCloseable` fixtures is removed.
- Display paths (console lines, `TaskResult.path()`, `Context.path()`, `TaskEvent.path()`, report `path` fields) separate
  levels with ` > ` instead of ` / `.

See [Migrating from 6.x](guides/migration-6-to-7).

## 6.x

See the versioned documentation for 6.x releases.
