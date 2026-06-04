---
title: Composition Building Blocks
description: Built-in action building blocks.
---

# Composition Building Blocks

Paramixel composes tests from action classes under `org.paramixel.api.action`. Each building block is built through a `Builder` and produces an immutable `Action` that can be nested into an action tree.

## Common blocks

| Building block | Purpose |
| --- | --- |
| `Step` | Execute a single context-aware operation. |
| `Sequence` | Execute child actions in order. |
| `Parallel` | Execute child actions concurrently. |
| `Scope` | Execute before/body/after with lifecycle guarantee (after always runs). |
| `Static` | Group static before/body/after work without fixture instance. |
| `Instance` | Create a fixture instance, run body, destroy instance. |
| `Repeat` | Repeat a child action. |
| `Timeout` | Bound child execution by a duration. |
| `Assert` | Evaluate a boolean condition against expected value. |
| `Delay` | Pause execution for a duration. |
| `Each` | Map iterables/streams into sequential or parallel child actions. |

## Fixture access

`Context` exposes fixture instances by type:

```java
import org.paramixel.api.Context;
import org.paramixel.api.action.Step;

var verify = Step.of(
        "verify fixture",
        Context.withInstance(MyFixture.class, fixture -> fixture.verify()));
```

Use `context.instance(Type.class)` for an `Optional` or `context.requireInstance(Type.class)` when the fixture is required.
