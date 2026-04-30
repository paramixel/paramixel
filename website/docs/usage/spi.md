---
title: Extension Points
description: Current public extension points.
---

# Extension Points

Paramixel does not currently expose a large separate SPI package.

The main supported extension points are the public core APIs:

## Custom actions

Implement `Action` directly or extend `org.paramixel.core.action.AbstractAction`.

For custom actions, implement `execute(Context)`.

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
