---
title: When to Use Paramixel
description: The problems Paramixel is designed for, and the ones it is not.
---

# When to Use Paramixel

Paramixel is a structured test-orchestration framework for Java integration, compatibility, and lifecycle-heavy testing.

## Good fit

- **Per-environment matrices.** One fixture per container, browser, database version, or protocol variant, bounded by a
  named pool.
- **Expensive fixtures with strict cleanup.** Setup, several checks, teardown, and a guaranteed finalizer even when a
  check fails.
- **Ordered flows with an explicit stop policy.** Dependent sequences for steps that build on each other, independent
  sequences for checks that should all run.
- **Bounded polling.** `repeat` with an iteration bound and an optional stop predicate instead of hand-written loops.
- **Cooperative deadlines.** `timeout` with interruption and honest reporting of callbacks that do not stop.

## Poor fit

- **Unit tests of pure functions.** A conventional unit-test framework is simpler.
- **A distributed workflow engine.** Paramixel schedules in-process tasks; it is not a cluster scheduler.
- **A container manager.** It orchestrates test fixtures; container lifecycle belongs to Testcontainers or similar.
- **Crash-safe exactly-once execution.** A JVM crash cannot be recovered by the framework.
- **Sandboxing untrusted code.** Discovered factories and callbacks are trusted executable code.

## What it deliberately does not do

- Preempt a running callback or forcibly stop Java threads.
- Make a shared fixture thread-safe.
- Close an `AutoCloseable` implicitly.
- Guarantee fairness or starvation freedom for unrelated branches.
