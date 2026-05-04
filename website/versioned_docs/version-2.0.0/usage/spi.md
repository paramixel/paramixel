---
title: Extension Points
description: Current public extension points.
---

# Extension Points

Paramixel does not currently expose a large separate SPI package.

The main supported extension points are the public core APIs:

## Custom actions

Implement `Action` directly or extend `AbstractAction`.

For most custom actions, extending `AbstractAction` is the simplest option because it provides:

- ID generation (4-character unique string) and name handling
- parent tracking (atomic, one-time-only assignment with self-parent and re-assignment checks)
- default `skip(Context)` behavior
- shared child validation for composite actions

### Action hierarchy

```
Action (interface)
  └─ AbstractAction (abstract)
       ├─ LeafAction (abstract) — no children
       │    ├─ Direct — takes Executable callback
       │    └─ Noop — always passes
       └─ BranchAction (abstract) — has children
           ├─ Sequential
           ├─ DependentSequential
           ├─ RandomSequential
           ├─ DependentRandomSequential
           ├─ Parallel
           └─ Lifecycle
```

- `LeafAction` provides `skip()` that returns a single skipped result
- `BranchAction` provides `skip()` that recursively skips all children and `computeStatus(List<Result>)` for computing parent status from children

If you implement `Action` directly, your implementation must:

- maintain its own parent reference
- implement `setParent(Action parent)` using atomic compare-and-set or equivalent synchronization
- reject `null` parents
- reject setting itself as its own parent
- reject assigning a second parent (must be thread-safe)

In both cases, custom actions implement `execute(Context)`. Use direct `Action` implementations only when you need full control over the action model.

## Pre-execution validation

When implementing custom composite actions, be aware that `DefaultRunner` validates the action tree before execution:

- `CycleLoopDetector` rejects parent-child cycles — throws `CycleDetectedException`
- `DeadlockDetector` rejects nested `Parallel` configurations that would cause thread starvation — throws `DeadlockDetected`

Custom actions that introduce cycles or deeply nested parallel structures will be caught by these validators.

## Custom listeners

Implement `Listener` and pass it to `Runner.builder().listener(...)`.

All callbacks receive `Result` (not `Context` + `Action`):

```java
Listener listener = new Listener() {
    @Override
    public void beforeAction(Result result) {
        System.out.println("starting " + result.getAction().getName());
    }
};
```

## Custom runner configuration

Use `Runner.builder()` to provide:

- configuration
- listener
- executor service

## Discovery selection

Use `Resolver` and `Selector` to control which factories are discovered.

`Selector.builder()` supports:

- `packageMatch(String regex)` / `packageOf(Class<?>)`
- `classMatch(String regex)` / `classOf(Class<?>)`
- `tagMatch(String regex)` — matches `@Paramixel.Tag` values

If you need deeper framework integration, treat the current public types in `org.paramixel.core` as the supported boundary.