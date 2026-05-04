---
title: Integration Testing
description: Patterns for stateful tests and external resources.
---

# Integration Testing

A common Paramixel pattern is:

1. create resources in `Lifecycle.before`
2. store them with `Context#getStore()#put(...)`
3. use them from descendants via `findAncestor(...)#getStore()#get(...)`
4. release them in `Lifecycle.after`

## Example shape

```java
Action action = Lifecycle.of(
        "environment",
        Direct.of("before", context -> {
            context.getStore().put("env", Value.of(startEnvironment()));
        }),
        Parallel.of(
                "tests",
                Direct.of("check A", context -> {
                    Environment env = context.findAncestor(1)
                            .orElseThrow()
                            .getStore()
                            .get("env")
                            .map(Value::get)
                            .map(v -> (Environment) v)
                            .orElseThrow();
                }),
                Direct.of("check B", context -> {})),
        Direct.of("after", context -> {
            Environment env = context.getStore()
                    .remove("env")
                    .map(Value::get)
                    .map(v -> (Environment) v)
                    .orElseThrow();
            stopEnvironment(env);
        }));
```

## Real examples

See:

- `core-examples/src/main/java/examples/testcontainers/kafka/KafkaTest.java`
- `core-examples/src/main/java/examples/testcontainers/mongodb/MongoDBTest.java`
- `core-examples/src/main/java/examples/testcontainers/nginx/NginxTest.java`

These examples use `Lifecycle`, `Store`, and `Cleanup` to manage Testcontainers resources.