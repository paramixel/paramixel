---
title: Examples
description: What the examples module demonstrates.
---

# Examples

The `examples` module holds definitions executed by the Paramixel Maven plugin during `test`. Run them with:

```bash
./mvnw test -pl examples        # requires Docker for the Altcontainers examples
```

| Package | Demonstrates |
| --- | --- |
| `examples` | Parallel and sequential matrices, argument variants, assertion and sleep steps, nested pools, and hooks |
| `examples.lifecycle` | Static setup, fixture setup and teardown, per-test before/after hooks, explicit cleanup |
| `examples.isolated` | Exclusive lock serialization inside parallel work |
| `examples.loop` | Bounded repetition with fixed and exponential delays, with and without a stop predicate |
| `examples.retry` | Retry after failure, fixture rebuilt per attempt, linear and exponential backoff, attempt history |
| `examples.argument` | Heterogeneous argument types in one matrix without casts |
| `examples.annotation` | `@Disabled` behavior |
| `examples.annotation.tags` | Tag-based selection across several factories |
| `examples.fixtures` | The fixture-per-environment shape with a bounded pool |
| `examples.altcontainers` | Kafka, MongoDB, and Nginx environments with real containers |

Each runnable package contains a `__ParamixelRunner__` entry point that runs everything in that package with a package
selector. The Altcontainers examples read image versions from `docker-images.txt` resources and share the
`environments` pool so only a bounded number of containers are alive at once.
