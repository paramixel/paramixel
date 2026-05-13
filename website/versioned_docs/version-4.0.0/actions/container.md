---
title: Container
description: Ordered composition with optional setup and cleanup.
---

# Container

`Container` is the ordered composition action in 4.x. It replaces `Sequential`, `DependentSequential`, `RandomSequential`, `DependentRandomSequential`, and `Lifecycle`.

## Builder

```java
Action before = before();
Action step1 = step1();
Action step2 = step2();
Action after = after();

Container.builder("flow")
        .before(before)    // optional
        .child(step1)      // zero or more body children
        .child(step2)
        .after(after)      // optional
        .build();
```

An empty container is invalid. A container with only `before`, only `after`, or only body children is valid.

## Policy

Container-specific behavior is configured with `Container.Policy`:

```java
Container.Policy.builder()
        .childMode(Container.ChildMode.INDEPENDENT)
        .orderMode(Container.OrderMode.SHUFFLED)
        .seed(42L)
        .build();
```

Defaults:

- `ChildMode.DEPENDENT`
- `OrderMode.DECLARED`
- no seed

`ChildMode.DEPENDENT` stops body execution after the first failed or skipped child and skips remaining body children.

`ChildMode.INDEPENDENT` runs all body children even after earlier failures or skips.

`OrderMode.DECLARED` uses builder order.

`OrderMode.SHUFFLED` shuffles body children before execution. `seed(long)` makes the shuffle deterministic when `SHUFFLED` is used; the seed is accepted but has no effect for `DECLARED` ordering.

## Example with static action methods

```java
public class MyTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action before = before();
        Action produce = produce();
        Action consume = consume();
        Action after = after();

        return Container.builder("kafka")
                .before(before)
                .child(produce)
                .child(consume)
                .after(after)
                .build();
    }

    private static Action before() {
        return Direct.builder("before")
                .runnable(context -> { /* setup */ })
                .build();
    }

    private static Action produce() {
        return Direct.builder("produce")
                .runnable(context -> { /* produce message */ })
                .build();
    }

    private static Action consume() {
        return Direct.builder("consume")
                .runnable(context -> { /* consume message */ })
                .build();
    }

    private static Action after() {
        return Direct.builder("after")
                .runnable(context -> { /* cleanup */ })
                .build();
    }
}
```

`Container` shares its context with `before` and `after` actions. Body children each receive an isolated child context. To access data stored by `before` from a body child, use `context.getAncestor("../").getStore()`.

For a full example showing context store usage across a deep Container hierarchy (suite → arguments → tests), see `examples/src/main/java/examples/lifecycle/FullLifecycleTest.java`.
