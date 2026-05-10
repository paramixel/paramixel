---
title: Migration 1.x to 3.x
description: Breaking API changes from 1.x to 3.x.
---

# Migration 1.x to 3.x

3.x replaces the old specialized action classes with `Container`, `Parallel`, `Direct`, and `Noop`.

Common mappings:

- `Direct.of("step", ctx -> {})` becomes `Direct.builder("step").execute(ctx -> {}).build()`.
- `Sequential` becomes `Container` with `ChildMode.INDEPENDENT` and `OrderMode.DECLARED`.
- `DependentSequential` becomes the default `Container` policy.
- `RandomSequential` becomes `Container.Policy` with `OrderMode.SHUFFLED` and `ChildMode.INDEPENDENT`.
- `DependentRandomSequential` becomes `Container` with `ChildMode.DEPENDENT` and `OrderMode.SHUFFLED`.
- `Lifecycle` becomes `Container.before(...).child(...).after(...)`.
- `Parallel.of(...)` becomes `Parallel.builder(...).child(...).build()`.

Context sharing is now action-owned. Use `Action.ContextMode.SHARED` on actions that should use the parent context directly.
