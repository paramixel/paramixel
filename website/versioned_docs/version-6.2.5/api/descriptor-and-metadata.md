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
- status predicates: `isPassed()`, `isFailed()`, `isSkipped()`, `isAborted()`, `isCompleted()`
- timestamps: `startedAt()`, `completedAt()`
- detail: `message()`, `throwable()`
- tree navigation: `before()`, `children()`, `after()`

## Execution state and identity

Use `Descriptor` for execution identity, tree navigation, timestamps, status predicates, messages, and throwables. Reusing the same `Action` instance in multiple locations creates distinct descriptors with independent execution state.
