---
title: Data-Driven Testing
description: Build action trees from collections with each().
---

# Data-Driven Testing

`each(items, mapper)` on `Sequential.Builder` and `Parallel.Builder` adds one child per item. It is `child(...)`
called once per item, in iteration order, so it mixes freely with fixed `child(...)` calls:

```java
import java.util.List;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

var users = List.of("ada", "grace", "linus");

Action spec = sequential("user checks")
        .each(users, user -> sequential("check " + user)
                .child(step("load " + user, ctx -> loadUser(user)))
                .child(step("verify " + user, ctx -> verifyUser(user)))
                .build())
        .build();
```

Use `Sequential` when order matters. Use `Parallel` when cases are independent and safe to run concurrently; the
pool name is mandatory and bounds the live children:

```java
Action matrix = parallel("browser matrix", "docker")
        .each(browsers, browser -> step("run " + browser, ctx -> runBrowserScenario(browser)))
        .build();
```

## Semantics

- The mapper runs **eagerly**, during definition, once per item in iteration order. A throw from the mapper is a
  define-time failure, and the mapper must not touch a fixture.
- The mapper must not return `null`; a null item mapping throws `NullPointerException`.
- `null` items or a `null` mapper throw `NullPointerException`.
- An empty iterable adds no children; an empty container is valid.
- Filtering and ordering happen in plain Java before the call (`users.stream().filter(...).toList()`); there are no
  stream overloads or collectors, because a builder is mutable and not thread-safe.

## Fixtures per item

The most common data-driven shape is one typed fixture per item:

```java
parallel("MetricsTest", "docker")
        .each(Environment.supported(), environment -> of(
                        environment.name(), MetricsFixture.class, () -> new MetricsFixture(environment))
                .before("setUp()", MetricsFixture::setUp)
                .body(sequential("tests")
                        .child(step("testHealthy()", MetricsFixture.class, MetricsFixture::testHealthy))
                        .child(step("testMetrics()", MetricsFixture.class, MetricsFixture::testMetrics)))
                .after("tearDown()", MetricsFixture::tearDown))
        .build();
```

The factory runs lazily when that fixture's node executes, so `paramixel.parallelism.docker` bounds how many
fixtures are alive at once. Keep data immutable or isolated per child when using parallel execution.
