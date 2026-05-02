---
title: Extension Points
description: Current public extension points.
---

# Extension Points

Paramixel does not currently expose a large separate SPI package.

The main supported extension points are the public core APIs:

## Custom actions

Implement `Action` directly or extend `org.paramixel.core.action.AbstractAction`.

For most custom actions, extending `AbstractAction` is the simplest option because it provides:

- ID generation (4-character unique string) and name handling
- parent tracking
- default `skip(Context)` behavior
- shared child validation for composite actions

If you implement `Action` directly, your implementation must:

- maintain its own parent reference
- implement `setParent(Action parent)` using atomic compare-and-set or equivalent synchronization
- reject `null` parents
- reject setting itself as its own parent
- reject assigning a second parent (must be thread-safe)

In both cases, custom actions implement `execute(Context)`. Use direct `Action` implementations only when you need full control over the action model.

## Custom listeners

Implement `Listener` and pass it to `Runner.builder().listener(...)`.

## Custom runner configuration

Use `Runner.builder()` to provide:

- configuration
- listener
- executor service

## Discovery selection

Use `Resolver` and `Selector` to control which factories are discovered.

If you need deeper framework integration, treat the current public types in `org.paramixel.core` as the supported boundary.
