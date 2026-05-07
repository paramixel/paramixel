---
title: Integration Testing
description: Patterns for stateful tests and external resources.
---

# Integration Testing

A common Paramixel pattern is:

1. create resources in `Container.before(...)`
2. store them with `Context#getStore()#put(...)`
3. use them from descendants via `findAncestor(...)#getStore()#get(...)`
4. release them in `Container.after(...)`

## Example using static action methods

This example uses the static action method pattern for IDE navigability:

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
                    LOGGER.info("[%s] initialize test environment ...", environment.name());

                    Network network = NetworkFactory.createNetwork();

                    environment.initialize(network);
                    assertThat(environment.isRunning()).isTrue();

                    context.getStore().put(ENVIRONMENT, Value.of(environment));
                })
                .build();
    }

    private static Action test() {
        return Direct.builder("test")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> {
                    KafkaTestEnvironment testEnvironment =
                            context.getStore().get(ENVIRONMENT).orElseThrow().cast(KafkaTestEnvironment.class);

                    LOGGER.info("[%s] testing ...", testEnvironment.name());

                    // test logic here
                })
                .build();
    }

    private static Action after(KafkaTestEnvironment environment) {
        return Direct.builder("after")
                .contextMode(Action.ContextMode.SHARED)
                .execute(context -> {
                    LOGGER.info("[%s] destroy test environment ...", environment.name());

                    var removedEnvironment = context.getStore().remove(ENVIRONMENT);
                    if (removedEnvironment.isPresent()) {
                        KafkaTestEnvironment testEnvironment =
                                removedEnvironment.orElseThrow().cast(KafkaTestEnvironment.class);
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
            .contextMode(Action.ContextMode.SHARED)
            .execute(context -> context.getStore().put("env", Value.of(startEnvironment())))
            .build();
}

private static Action test() {
    return Direct.builder("test")
            .contextMode(Action.ContextMode.SHARED)
            .execute(context -> {
                Environment env = context.getStore()
                        .get("env")
                        .orElseThrow()
                        .cast(Environment.class);
                // use env
            })
            .build();
}

private static Action after() {
    return Direct.builder("after")
            .contextMode(Action.ContextMode.SHARED)
            .execute(context -> {
                Environment env = context.getStore()
                        .remove("env")
                        .orElseThrow()
                        .cast(Environment.class);
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

## Real examples

See:

- `examples/src/main/java/examples/testcontainers/kafka/KafkaTest.java`
- `examples/src/main/java/examples/testcontainers/mongodb/MongoDBTest.java`
- `examples/src/main/java/examples/testcontainers/nginx/NginxTest.java`

These examples use `Container`, `Store`, and `Cleanup` to manage Testcontainers resources with the static action method pattern.
