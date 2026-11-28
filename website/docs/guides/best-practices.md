---
title: Best Practices
description: Patterns that keep definitions readable and results trustworthy.
---

# Best Practices

## One fixture per environment

Build the fixture once per environment in that branch's `before` slot, bind what already exists, and let nested groups
read it from the shared scope. Avoid capturing shared mutable state in static fields when branches run in parallel.

## Name every boundary

Named groups and steps are what a failing report shows first. `Sequential.builder("tests")` keeps a readable reporting
boundary without creating a scope.

## Prefer `after` over implicit cleanup

The `after` slot runs once the node has started, whatever failed, and an `after` subtree keeps running when the run is
canceled. Compose several cleanup phases with
`.after(Sequential.builder("cleanup").independent().step(a).step(b))`, because a sequential cleanup is `dependent()` by
default. Do not rely on `AutoCloseable` being closed implicitly; Paramixel deliberately does not do it.

## Keep cleanup idempotent

A failing body still runs finalizers. Finalizers should tolerate partial setup and be safe to run when the operation did
not complete; use `Context.find` in the `after` slot for anything a failed `before` may not have stored.

## Treat retry as a policy, not a loop

Retry only statuses you mean to retry. Cancellation and assumption aborts are never retried automatically. Prefer
`repeat` with `untilPassed` when replay is the intent.

## Use timeouts with cooperative code

Timeouts interrupt the running callback and stop admitting new work. Code that ignores interruption produces a
non-quiescent result and withheld cleanup; check `result.isQuiescent()` in CI if you rely on hard deadlines.

## Keep pools honest

Define every named pool, do not nest the same pool, and size pools for the resource they protect (containers, ports,
licenses), not for CPU count. A capacity is a cap, not a reservation: it does not guarantee concurrency, so raising it
above `paramixel.workers` changes nothing.

## Bind what exists, put what you create

`bind` values are rendered into report files as plan parameters; `put` values are never reported. Declaring what already
exists with `bind` is what makes a report readable without leaking runtime objects.

## Validate before running

Because compilation is side-effect free, a plan error means no fixture was created. Test definitions with a failing plan
deliberately to confirm the compiler catches it.

## Report from the result

Console output, report files, and integrations should all read `Result`. Do not reconstruct status from logs.
