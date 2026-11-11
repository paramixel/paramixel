---
title: Introduction
description: What Paramixel is and how a definition becomes a result.
---

# Introduction

Paramixel builds tests from **immutable task definitions**. A definition declares a tree of tasks with lifecycle
callbacks, instance factories, named concurrency budgets, conditions, repetition, retries, and timeouts. The compiler
validates the tree; the scheduler executes it with bounded concurrency; the run produces one immutable `Result`.

## The pieces

- **`Task`** — an immutable definition: name, execution mode, children, callbacks, attributes.
- **`TaskBuilder`** — the typed authoring surface that produces a `Task` snapshot.
- **`CompiledPlan`** — the internal, immutable occurrence tree with validated resources.
- **`TaskResult`** — an immutable execution occurrence with status, phases, attempts, timings, and failures.
- **`Runner`** — compiles, schedules, and reports; one run at a time per instance.

## Why definitions are separate from execution

A definition can be validated without side effects, reused across runs, and compiled into distinct occurrences with
their own identity and owned instances. A validation error therefore guarantees that no factory, lifecycle callback, or
test body ran.

## What a run reports

`Result.status()` is the semantic aggregate; `Result.exitCode()` applies the configured failure mapping.
`Result.isQuiescent()` is `false` only when a callback ignored cooperative cancellation, in which case cleanup was
withheld and the run is not claimed to be cleanly finalized.

## Next

- [Quick start](../getting-started/first-test)
- [Tasks](../core-concepts/actions)
- [Parallel execution](../guides/parallel-execution)
