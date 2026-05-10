---
title: Action Composition
description: Combine built-in actions into test trees.
---

# Action Composition

Paramixel test plans are ordinary `Action` trees. In 3.x, ordered composition uses `Container` and concurrent composition uses `Parallel`.

Composition actions are builder-only. `Noop.of(...)` remains as the compact factory for placeholder actions.

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action setup = setup();
        Action test = test();
        Action cleanup = cleanup();

        return Container.builder("suite")
                .before(setup)
                .child(test)
                .after(cleanup)
                .build();
    }

    private static Action setup() {
        return Direct.builder("setup").execute(context -> {}).build();
    }

    private static Action test() {
        return Direct.builder("test").execute(context -> {}).build();
    }

    private static Action cleanup() {
        return Direct.builder("cleanup").execute(context -> {}).build();
    }
}
```

Use `Container.Policy` for run-all, fail-fast, declared, or shuffled body execution.

## Common composition patterns

### Ordered run-all

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action step1 = step1();
        Action step2 = step2();

        return Container.builder("suite")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build())
                .child(step1)
                .child(step2)
                .build();
    }

    private static Action step1() {
        return Direct.builder("step 1").execute(context -> {}).build();
    }

    private static Action step2() {
        return Direct.builder("step 2").execute(context -> {}).build();
    }
}
```

### Ordered fail-fast

`Container` defaults to dependent, declared-order body execution.

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action step1 = step1();
        Action step2 = step2();

        return Container.builder("suite")
                .child(step1)
                .child(step2)
                .build();
    }

    private static Action step1() {
        return Direct.builder("step 1").execute(context -> {}).build();
    }

    private static Action step2() {
        return Direct.builder("step 2").execute(context -> {}).build();
    }
}
```

### Shuffled run-all

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action a = a();
        Action b = b();

        return Container.builder("suite")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .orderMode(Container.OrderMode.SHUFFLED)
                        .seed(1234L)
                        .build())
                .child(aAction)
                .child(bAction)
                .build();
    }

    private static Action a() {
        return Direct.builder("a").execute(context -> {}).build();
    }

    private static Action b() {
        return Direct.builder("b").execute(context -> {}).build();
    }
}
```

### Concurrent children

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action a = a();
        Action b = b();

        return Parallel.builder("suite")
                .parallelism(2)
                .child(aAction)
                .child(bAction)
                .build();
    }

    private static Action a() {
        return Direct.builder("a").execute(context -> {}).build();
    }

    private static Action b() {
        return Direct.builder("b").execute(context -> {}).build();
    }
}
```

### Setup / body / teardown

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action before = before();
        Action primary = primary();
        Action after = after();

        return Container.builder("suite")
                .before(before)
                .child(primary)
                .after(after)
                .build();
    }

    private static Action before() {
        return Direct.builder("before")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> context.getStore().put("token", Value.of("abc")))
                .build();
    }

    private static Action primary() {
        return Direct.builder("primary").execute(context -> {}).build();
    }

    private static Action after() {
        return Direct.builder("after").execute(context -> {}).build();
    }
}
```

## Nesting

Actions can be nested arbitrarily.

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action case1 = case1();
        Action finished = Noop.of("finished");

        return Container.builder("suite")
                .child(case1)
                .child(finished)
                .build();
    }

    private static Action case1() {
        Action before = before();
        Action checks = checks();
        Action after = after();

        return Container.builder("case 1")
                .before(before)
                .child(checks)
                .after(after)
                .build();
    }

    private static Action before() {
        return Direct.builder("before").execute(context -> {}).build();
    }

    private static Action checks() {
        Action a = a();
        Action b = b();

        return Parallel.builder("checks")
                .child(aAction)
                .child(bAction)
                .build();
    }

    private static Action a() {
        return Direct.builder("a").execute(context -> {}).build();
    }

    private static Action b() {
        return Direct.builder("b").execute(context -> {}).build();
    }

    private static Action after() {
        return Direct.builder("after").execute(context -> {}).build();
    }
}
```

## Choosing a Container Policy

- `ChildMode.INDEPENDENT` runs every body child.
- `ChildMode.DEPENDENT` stops body execution after the first failed or skipped child.
- `OrderMode.DECLARED` runs body children in builder order.
- `OrderMode.SHUFFLED` shuffles body children, optionally with `seed(...)`.

## Argument testing pattern

When running the same test logic against multiple inputs, use a builder with a loop to add children dynamically:

```java
var builder = Container.builder("suite")
        .policy(Container.Policy.builder()
                .childMode(Container.ChildMode.INDEPENDENT)
                .build());
for (String argument : arguments()) {
    builder.child(argument(argument));
}
return builder.build();
```

For concurrent argument execution, use `Parallel` instead:

```java
var builder = Parallel.builder("suite").parallelism(4);
for (String argument : arguments()) {
    builder.child(argument(argument));
}
return builder.build();
```

See [Argument Testing](argument-testing.md) for the full pattern with per-argument lifecycle, context sharing, and type examples.

## Action factory method pattern

For readability and IDE navigability, create each action using a `private static` method. This pattern makes the test structure visible in the IDE outline and allows direct click-to-navigate for each action.

### Basic pattern

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action first = first();
        Action second = second();

        return Container.builder("MyTest")
                .child(first)
                .child(second)
                .build();
    }

    private static Action first() {
        return Direct.builder("first")
                .execute(context -> { /* test logic */ })
                .build();
    }

    private static Action second() {
        return Direct.builder("second")
                .execute(context -> { /* test logic */ })
                .build();
    }
}
```

### Integration test pattern

For integration tests with setup/teardown, extract each lifecycle phase into its own method:

```java
public class KafkaTest {

    private static final String ENVIRONMENT = "environment";

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        var parallelBuilder = Parallel.builder("KafkaExample");
        for (KafkaTestEnvironment environment : createTestEnvironments()) {
            Action argumentContainer = argument(environment);
            parallelBuilder.child(argumentContainer);
        }
        return parallelBuilder.build();
    }

    private static Action argument(KafkaTestEnvironment environment) {
        Action before = before(environment);
        Action test = test();
        Action after = after(environment);

        return Container.builder(environment.name())
                .before(before)
                .child(test)
                .after(after)
                .build();
    }

    private static Action before(KafkaTestEnvironment environment) {
        return Direct.builder("before")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> {
                    environment.initialize();
                    context.getStore().put(ENVIRONMENT, Value.of(environment));
                })
                .build();
    }

    private static Action test() {
        return Direct.builder("test")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> {
                    KafkaTestEnvironment env = context.getStore()
                            .get(ENVIRONMENT)
                            .orElseThrow()
                            .cast(KafkaTestEnvironment.class);
                    // test logic using env
                })
                .build();
    }

    private static Action after(KafkaTestEnvironment environment) {
        return Direct.builder("after")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> {
                    KafkaTestEnvironment env = context.getStore()
                            .remove(ENVIRONMENT)
                            .orElseThrow()
                            .cast(KafkaTestEnvironment.class);
                    env.close();
                })
                .build();
    }
}
```

### Benefits

1. **IDE navigation** — Each `private static Action` method appears in the IDE outline. Click to navigate directly to any action's implementation.

2. **Readable structure** — The `actionFactory()` method reads as a high-level outline of the test structure.

3. **Builder + loop pattern** — When adding multiple children, create the builder first, then loop to add children:

```java
var builder = Container.builder("suite")
        .policy(Container.Policy.builder()
                .childMode(Container.ChildMode.INDEPENDENT)
                .build());
for (String name : names) {
    Action action = createTest(name);
    builder.child(action);
}
return builder.build();
```

4. **Local variables** — Always assign actions to local variables before passing to builders. This makes the code scannable and avoids inline method calls inside builder chains.
