---
title: Argument Testing
description: Run the same test logic against multiple inputs.
---

# Argument Testing

Argument testing runs the same test logic against multiple inputs — different configurations, data sets, or environment versions. This is the most common Paramixel pattern for parameterized testing.

## Builder + loop pattern

Create a builder, loop over your arguments, and add each as a child:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    var builder = Container.builder("MyTest")
            .policy(Container.Policy.builder()
                    .childMode(Container.ChildMode.INDEPENDENT)
                    .build());
    for (String argument : List.of("arg-0", "arg-1", "arg-2")) {
        builder.child(argument(argument));
    }
    return builder.build();
}
```

Use `Container` for sequential execution and `Parallel` for concurrent running.

## Sequential arguments

Use `Container` with `ChildMode.INDEPENDENT` to run every argument regardless of earlier results:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    var builder = Container.builder("SequentialTest")
            .policy(Container.Policy.builder()
                    .childMode(Container.ChildMode.INDEPENDENT)
                    .build());
    for (String arg : arguments()) {
        builder.child(argument(arg));
    }
    return builder.build();
}

private static Action argument(String arg) {
    return Direct.builder(arg)
            .runnable(context -> { /* test logic */ })
            .build();
}
```

Omit the policy (or use the default `DEPENDENT`) for fail-fast behavior that stops after the first failure.

## Parallel arguments

Use `Parallel` to run arguments concurrently:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    var builder = Parallel.builder("ParallelTest")
            .parallelism(4);
    for (String arg : arguments()) {
        builder.child(argument(arg));
    }
    return builder.build();
}
```

The `parallelism` value controls the maximum number of concurrent arguments.

## Per-argument lifecycle

Add `before` and `after` hooks to each argument container for per-argument setup and teardown:

```java
private static Action argument(String arg) {
    Action before = before(arg);
    Action test = test(arg);
    Action after = after(arg);

    return Container.builder(arg)
            .before(before)
            .child(test)
            .after(after)
            .build();
}
```

With a `Container`, `before` and `after` actions share the same context store. Body children receive isolated child contexts:

```java
private static Action before(String arg) {
    return Direct.builder("before")
            .runnable(context -> {
                MyEnvironment env = startEnvironment(arg);
                context.getStore().put("env", env);
            })
            .build();
}

private static Action test(String arg) {
    return Direct.builder("test")
            .runnable(context -> {
                MyEnvironment env = context.getAncestor("../")
                        .getStore()
                        .get("env", MyEnvironment.class)
                        .orElseThrow();
                // test logic using env
            })
            .build();
}

private static Action after(String arg) {
    return Direct.builder("after")
            .runnable(context -> {
                MyEnvironment env = context.getStore()
                        .remove("env", MyEnvironment.class)
                        .orElseThrow();
                env.close();
            })
            .build();
}
```

## Argument types

Any Java type can serve as argument data.

### Primitive types

```java
for (int value : new int[]{1, 2, 3}) {
    builder.child(Direct.builder("int-" + value)
            .runnable(context -> assertThat(value).isPositive())
            .build());
}
```

### Custom types

Records, enums, and classes work naturally:

```java
record Sample(String name, int count) {}

for (Sample sample : List.of(new Sample("a", 1), new Sample("b", 2))) {
    builder.child(Direct.builder(sample.name())
            .runnable(context -> assertThat(sample.count()).isPositive())
            .build());
}
```

### Collection types

```java
for (List<String> items : List.of(List.of("a", "b"), List.of("x", "y"))) {
    builder.child(Direct.builder("list-" + items.get(0))
            .runnable(context -> assertThat(items).hasSize(2))
            .build());
}
```

## Cross-argument data

Each argument has its own isolated context store by default. Use `findAncestor()` to navigate up the context hierarchy and access data from an ancestor:

```java
// Store at the argument container level (from a SHARED before action)
context.getAncestor("../../").getStore().get("arg-key");
```

The ancestor path depends on the nesting depth:

- `findAncestor("../")` — parent
- `findAncestor("../../")` — grandparent
- `findAncestor("/")` — root

## Custom scheduler

For parallel arguments that need custom scheduling semantics, provide an `AsyncScheduler` to the `Parallel` node:

```java
private static final AsyncScheduler scheduler = (action, context) -> {
    try {
        return CompletableFuture.completedFuture(action.run(context));
    } catch (Throwable throwable) {
        return CompletableFuture.failedFuture(throwable);
    }
};

@Paramixel.ActionFactory
public static Action actionFactory() {
    var builder = Parallel.builder("CustomExecutorTest")
            .scheduler(scheduler);
    for (String arg : arguments()) {
        builder.child(argument(arg));
    }

    return builder.build();
}
```

The scheduler applies to the `Parallel` subtree. Nested `context.runAsync(...)` calls from inside those argument actions use the same scheduler.

When a custom scheduler is used, `Parallel.parallelism` still limits direct argument admission for that `Parallel` node, but default scheduler worker limits no longer control work inside the custom-scheduled subtree. The custom scheduler implementation is responsible for any executor sizing, queuing, backpressure, or nested `context.runAsync(...)` parallelism limits it requires.

## Full example with Testcontainers

See [Integration Testing](integration-testing.md) for a complete example using parallel arguments with Testcontainers environments.
