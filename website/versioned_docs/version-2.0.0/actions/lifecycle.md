---
title: Lifecycle
description: before/main/after execution.
---

# Lifecycle

`Lifecycle` groups three actions:

```java
Lifecycle.of(String name, Action before, Action main, Action after)
```

## Semantics

Execution order is always:

1. `before`
2. `main` or a recursive skip of `main`
3. `after`

Important behavior from the current implementation:

- `after` always runs
- if `before` fails, `main` is skipped
- if `before` skips, `main` is skipped
- if `main` fails, the lifecycle fails
- if `main` skips, the lifecycle skips unless `after` fails
- if `after` fails, the lifecycle result becomes `FAIL`

## Context hierarchy and Store

`before` and `after` share the same lifecycle child context.
`main` runs in a child of that lifecycle context.

That means data stored in `before` is typically read from `main` via `context.findAncestor(1).getStore()`.

When `main` is skipped (because `before` failed or skipped), each skipped action and its descendants receive their own child contexts, mirroring the action tree. Listener callbacks interleave the same way as normal execution: parent `beforeAction`, then children, then parent `afterAction`.

## Example

```java
Action suite = Lifecycle.of(
        "suite",
        Direct.of("before", context -> {
            context.getStore().put("ready", Value.of(true));
        }),
        Direct.of("main", context -> {
            boolean ready = context.findAncestor(1)
                    .getStore()
                    .get("ready")
                    .map(Value::get)
                    .map(v -> (Boolean) v)
                    .orElseThrow();
        }),
        Direct.of("after", context -> {
            context.getStore().clear();
        }));
```

## Cleanup note

`Lifecycle` itself does not attach suppressed exceptions to earlier failures. If you need aggregated cleanup failures, use [`Cleanup`](../usage/cleanup).