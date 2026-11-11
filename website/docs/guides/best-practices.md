---
title: Best Practices
description: Patterns that keep definitions readable and results trustworthy.
---

# Best Practices

## One instance per environment

Use `forInstance` once per environment and let nested groups inherit it. Avoid capturing shared mutable state in static
fields when children run in parallel.

## Name every boundary

Named groups and steps are what a failing report shows first. `sequential("tests", …)` keeps a readable reporting
boundary without creating a scope.

## Prefer `after` over implicit cleanup

`after` runs in reverse declaration order and is always attempted. Do not rely on `AutoCloseable` being closed
implicitly; Paramixel deliberately does not do it.

## Keep cleanup idempotent

A failing body still runs finalizers. Finalizers should tolerate partial setup and be safe to run when the operation did
not complete.

## Treat retry as a policy, not a loop

Retry only statuses you mean to retry. Cancellation and assumption aborts are never retried automatically. Prefer
`repeat` with `untilPassed` when replay is the intent.

## Use timeouts with cooperative code

Timeouts interrupt the running callback and stop admitting new work. Code that ignores interruption produces a
non-quiescent result and withheld cleanup; check `result.isQuiescent()` in CI if you rely on hard deadlines.

## Keep pools honest

Define every named pool, do not nest the same pool, and size pools for the resource they protect (containers, ports,
licenses), not for CPU count.

## Validate before running

Because compilation is side-effect free, a plan error means no fixture was created. Test definitions with a failing plan
deliberately to confirm the compiler catches it.

## Report from the result

Console output, report files, and integrations should all read `Result`. Do not reconstruct status from logs.
