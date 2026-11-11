---
title: Descriptor
description: Execution-occurrence state for action trees.
---

# Descriptor

`Descriptor` represents one execution occurrence of an action in a run. It is not the reusable action definition; it is the runtime node that records state.

## API

A descriptor exposes:

- `parent()`
- `id()`
- `action()`
- names: `displayName()`, `path()` (the display-name path from the run root)
- state: `status()` (a `Status` with `kind()`, `message()` and `throwable()`), `isCompleted()`
- timing: `startedAt()`, `completedAt()`, `duration()`
- tree navigation: `parent()`, `before()`, `children()`, `after()`

## Execution state and identity

Use `Descriptor` for execution identity, tree navigation, timing, and outcome detail. Reusing the same `Action` instance in multiple locations creates distinct descriptors with independent execution state.
