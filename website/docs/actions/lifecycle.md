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

## Context hierarchy

`before` and `after` share the same lifecycle child context.
`main` runs in a child of that lifecycle context.

That means data attached in `before` is usually read from `main` via `context.findContext(1)` or `context.findAttachment(1)`.

## Example

Pattern used in `examples/test/lifecycle/FullLifecycleTest.java`:

```java
Action suite = Lifecycle.of(
        "suite",
        Direct.of("before", context -> context.setAttachment("ready")),
        Direct.of("main", context -> {
            String value = context.findAttachment(1)
                    .flatMap(a -> a.to(String.class))
                    .orElseThrow();
        }),
        Direct.of("after", context -> context.removeAttachment()));
```

## Cleanup note

`Lifecycle` itself does not attach suppressed exceptions to earlier failures. If you need aggregated cleanup failures, use [`Cleanup`](../usage/cleanup).
