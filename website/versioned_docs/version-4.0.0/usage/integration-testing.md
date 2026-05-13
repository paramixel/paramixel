---
title: Integration Testing
description: Patterns for stateful tests and external resources.
---

# Integration Testing

A common Paramixel pattern is:

1. create resources in `Container.before(...)`
2. store them with `Context#getStore()#put(...)`
3. use them from descendants via `getAncestor("../")#getStore()#get(...)`
4. release them in `Container.after(...)`

## Example using static action methods

This example uses the static action method pattern for IDE navigability:

```java
public class KafkaTest {

    private static final String ENVIRONMENT = "environment";

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() throws Throwable {
        var parallelBuilder = Parallel.builder("KafkaExample");
        for (KafkaTestEnvironment environment : KafkaTestEnvironment.createTestEnvironments()) {
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
                .runnable(context -> {
                    LOGGER.info("[%s] initialize test environment ...", environment.name());

                    Network network = NetworkFactory.createNetwork();

                    environment.initialize(network);
                    assertThat(environment.isRunning()).isTrue();

                    context.getStore().put(ENVIRONMENT, environment);
                })
                .build();
    }

    private static Action test() {
        return Direct.builder("test")
                .runnable(context -> {
                    KafkaTestEnvironment testEnvironment =
                            context.getAncestor("../")
                                    .getStore()
                                    .get(ENVIRONMENT, KafkaTestEnvironment.class)
                                    .orElseThrow();

                    LOGGER.info("[%s] testing ...", testEnvironment.name());

                    // test logic here
                })
                .build();
    }

    private static Action after(KafkaTestEnvironment environment) {
        return Direct.builder("after")
                .runnable(context -> {
                    LOGGER.info("[%s] destroy test environment ...", environment.name());

                    var removedEnvironment = context.getStore().remove(ENVIRONMENT, KafkaTestEnvironment.class);
                    if (removedEnvironment.isPresent()) {
                        KafkaTestEnvironment testEnvironment = removedEnvironment.orElseThrow();
                        testEnvironment.close();
                    }
                })
                .build();
    }
}
```

## Key patterns

### Store for shared state

Use `context.getStore()` to pass state between lifecycle phases:

```java
private static Action before() {
    return Direct.builder("before")
            .runnable(context -> context.getStore().put("env", startEnvironment()))
            .build();
}

private static Action test() {
    return Direct.builder("test")
            .runnable(context -> {
                Environment env = context.getAncestor("../")
                        .getStore()
                        .get("env", Environment.class)
                        .orElseThrow();
                // use env
            })
            .build();
}

private static Action after() {
    return Direct.builder("after")
            .runnable(context -> {
                Environment env = context.getStore()
                        .remove("env", Environment.class)
                        .orElseThrow();
                stopEnvironment(env);
            })
            .build();
}
```

### Builder + loop for multiple arguments

When running tests against multiple environments, use a builder with a loop:

```java
var parallelBuilder = Parallel.builder("Tests");
for (TestEnvironment environment : environments) {
    Action argumentContainer = argument(environment);
    parallelBuilder.child(argumentContainer);
}
return parallelBuilder.build();
```

## Cleanup utility pattern

When an integration test creates multiple resources that must be released in order, use `Cleanup`:

```java
private static Action after(KafkaTestEnvironment environment, Network network) {
    return Direct.builder("after")
            .runnable(context -> {
                Cleanup.of(Cleanup.Mode.FORWARD)
                        .addCloseable(environment)
                        .addCloseable(network)
                        .runAndThrow();
            })
            .build();
}
```

`Cleanup` collects `AutoCloseable` resources and runs them in forward or reverse order. `runAndThrow()` rethrows the first failure as the primary exception with later failures attached as suppressed exceptions. `OutOfMemoryError` and `StackOverflowError` abort the cleanup loop immediately.

### Conditional cleanup

Use `addWhen` for resources that may or may not need cleanup:

```java
Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);
cleanup.addCloseable(database);
cleanup.addWhen(useCache, cache::invalidate);
cleanup.runAndThrow();
```

## Real examples

See:

- `examples/src/main/java/examples/testcontainers/kafka/KafkaTest.java`
- `examples/src/main/java/examples/testcontainers/mongodb/MongoDBTest.java`
- `examples/src/main/java/examples/testcontainers/nginx/NginxTest.java`

These examples use `Container`, `Store`, and `Cleanup` to manage Testcontainers resources with the static action method pattern.
