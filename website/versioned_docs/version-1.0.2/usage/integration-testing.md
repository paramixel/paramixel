---
title: Integration Testing
description: Patterns for stateful tests and external resources.
---

# Integration Testing

A common Paramixel pattern is:

1. create resources in `Lifecycle.before`
2. store them with `Context#setAttachment(...)`
3. use them from descendants via `findAttachment(...)` or `findContext(...)`
4. release them in `Lifecycle.after`

## Example shape

```java
Action action = Lifecycle.of(
        "environment",
        Direct.of("before", context -> context.setAttachment(startEnvironment())),
        Parallel.of(
                "tests",
                Direct.of("check A", context -> {
                    Environment env = context.findAttachment(1)
                            .flatMap(a -> a.to(Environment.class))
                            .orElseThrow();
                }),
                Direct.of("check B", context -> {})),
        Direct.of("after", context -> stopEnvironment(context.removeAttachment())));
```

## Real examples

See:

- `examples/testcontainers/kafka/KafkaExample.java`
- `examples/testcontainers/mongodb/MongoDBExample.java`
- `examples/testcontainers/nginx/NginxExample.java`

These examples use `Lifecycle`, attachments, and `Cleanup` to manage Testcontainers resources.
