---
title: Troubleshooting
description: Common failures and the invariant behind them.
---

# Troubleshooting

## "Parallel pool 'x' is not defined"

A parallel task names a pool with no capacity. Set `paramixel.parallelism.x` or call
`Runner.builder().pool(Pool.named("x"), n)`. Discovery itself uses a pool named `discovery`, which defaults to
`paramixel.parallelism`.

## "Id \"x\" used by step(...) in a definition with no anchor"

The definition refers to a hook identifier but never names the class that declares it. Root the definition at
`Sequential.builder(MyTest.class, "name")` — one class literal per definition root, inherited by everything attached below
it.

## "Unknown id \"x\" used by step(...)"

No `@Paramixel.Id("x")` method is declared by the anchor class. Identifiers are class-scoped: inheritance is not
consulted, so a hook declared in a base class is never resolved and shared behaviour is a plain static delegation.

## "Context entry ... holds X but Y was requested"

`require(name, Type)` checks assignability against the stored value. Use `find(name, Type)` where absence or a different
type is expected — typically in an `after` hook releasing whatever a partial `before` managed to store.

## "Pool 'x' is already active on this ancestry path"

Nested groups named the same pool. Use a different pool name for nested groups; the restriction is intentional so a
capacity change cannot turn a passing plan into a deadlock.

## Everything is `SKIPPED`

- A dependent sequence stops after the first non-passing child, including `ABORTED` and `SKIPPED`.
- A `when(...)` condition evaluated to false.
- A body failed, so its children were skipped.

Inspect the child `reason()` and the parent `phases()`; a skipped child always carries an explicit reason.

## `result.isQuiescent()` is false

A callback ignored interruption after a timeout or cancellation. Cleanup was withheld on purpose to avoid touching
resources that are still being mutated, permits remain held, and the exit code is non-zero. Fix the callback to honor
interruption (for example, let `InterruptedException` propagate).

## A listener exception broke reporting but not the test

Listener failures are isolated and recorded as diagnostics. Check `result.diagnostics()` for `listener` entries.

## Nothing was discovered

`Runner.run()` with no matching `@Paramixel.TaskFactory` reports `SKIPPED` and fails by default
(`paramixel.failIfNoTests=true`). Check the selector, `@Disabled`, and tag filters.
