---
title: Release Notes
description: Notable changes by release.
---

# Release Notes

## 7.0.0

Greenfield refactor. The public action hierarchy was replaced by one immutable `Task` definition.

**Highlights**

- `Task` / `TaskBuilder` / `Tasks` replace `Action`, `ActionBuilder`, and the per-shape classes.
- `forInstance(name, supplier)` owns a lazily created instance per attempt with `before` / `after` lifecycle.
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
- `paramixel.failIfNoTests` and `paramixel.failureOnSkip` now default to `true`.

**Breaking changes**

- The `org.paramixel.api.action` package and the mutable `Descriptor` tree are removed.
- `Context.instance(Class)` is removed; typed callbacks and `Key<T>` resources replace it.
- Implicit closing of `AutoCloseable` fixtures is removed.

See [Migrating from 6.x](guides/migration-6-to-7).

## 6.x

See the versioned documentation for 6.x releases.
