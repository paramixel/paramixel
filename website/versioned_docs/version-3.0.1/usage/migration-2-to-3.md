---
title: Migration 2.x to 3.x
description: Breaking API changes from 2.x to 3.x.
---

# Migration 2.x to 3.x

3.x intentionally breaks the 2.x action model.

Removed actions:

- `Sequential`
- `DependentSequential`
- `RandomSequential`
- `DependentRandomSequential`
- `Lifecycle`

Use `Container` instead:

```java
Action before = before();
Action step1 = step1();
Action step2 = step2();
Action after = after();

Container.builder("case")
        .before(before)
        .child(step1)
        .child(step2)
        .after(after)
        .build();
```

Use `Container.Policy` for behavior that used to be encoded by specific class names.

```java
Container.Policy.builder()
        .childMode(Container.ChildMode.INDEPENDENT)
        .orderMode(Container.OrderMode.SHUFFLED)
        .seed(42L)
        .build();
```

Fixed `findAncestor(...)` depths may need updates. Prefer `Action.ContextMode.SHARED` when setup/body/cleanup actions intentionally share the same store.

## Builder API changes

### `children(List<Action>)` removed

The `children(List<Action>)` method has been removed from both `Container.Builder` and `Parallel.Builder`. Use chained `.child()` calls instead:

**Before (2.x):**

```java
Container.builder("suite")
        .children(List.of(action1, action2, action3))
        .build();
```

**After (3.x):**

```java
Action action1 = createAction1();
Action action2 = createAction2();
Action action3 = createAction3();

Container.builder("suite")
        .child(action1)
        .child(action2)
        .child(action3)
        .build();
```

For dynamic child lists, use a builder with a loop:

```java
var builder = Container.builder("suite");
for (Action action : actions) {
    builder.child(action);
}
return builder.build();
```
