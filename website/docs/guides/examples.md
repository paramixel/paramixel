---
title: Examples
description: What the examples module demonstrates.
---

# Examples

The `examples` module holds definitions executed by the Paramixel Maven plugin during `test`. Run them with:

```bash
./mvnw test -pl examples        # requires Docker for the Testcontainers examples
```

| Package | Demonstrates |
| --- | --- |
| `examples` | Parallel and sequential matrices, assertion and sleep steps, isolation, retry, hooks |
| `examples.lifecycle` | Static setup, per-instance lifecycle, per-test before/after, explicit cleanup |
| `examples.isolated` | Exclusive lock serialization inside parallel work |
| `examples.loop` | Bounded repetition with fixed, exponential, and predicate-driven stops |
| `examples.retry` | Retry after failure, fresh instance per attempt, attempt history |
| `examples.argument` | Heterogeneous argument types in one matrix without casts |
| `examples.annotation` | `@Disabled` and tag selection |
| `examples.fixtures` | The fixture-per-environment shape with a bounded pool |
| `examples.testcontainers` | Kafka, MongoDB, and Nginx environments with real containers |

Each runnable package contains a `__ParamixelRunner__` entry point that runs everything in that package with a package
selector. The Testcontainers examples read image versions from `docker-images.txt` resources and share the
`environments` pool so only a bounded number of containers are alive at once.
