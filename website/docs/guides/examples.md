---
title: Real-World Test Shapes
description: Source-backed example patterns.
---

# Real-World Test Shapes

The repository's examples live under `examples/src/main/java`. They demonstrate factory discovery, annotations, lifecycle composition, retry support, and Testcontainers integration.

## Factory with tags

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

@Paramixel.Factory
@Paramixel.Tag("smoke")
@Paramixel.Tag("critical")
public static Action smoke() {
    return Sequential.sequential("smoke")
            .child(Step.of("arrange", ctx -> arrange()))
            .child(Step.of("act", ctx -> act()))
            .child(Step.of("assert", ctx -> assertResult()))
            .build();
}
```

## AnnotationResolver pattern

```java
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Sequential;

final class LoginFixture {
    @Paramixel.Id("open")
    public void open() {}

    @Paramixel.Id("submit")
    public void submit() {}
}

var resolver = AnnotationResolver.create(LoginFixture.class);
Action fixture = Instance.instance(LoginFixture.class)
        .body(Sequential.sequential("login")
                .child(resolver.byId("open"))
                .child(resolver.byId("submit"))
                .build())
        .build();
```

## Disabled factory

```java
@Paramixel.Factory
@Paramixel.Disabled("under investigation")
public static Action temporarilyDisabled() {
    return Sequential.sequential("disabled").build();
}
```

## Named builders

The `ActionsExample` in the repository demonstrates the named builder factories:

```java
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Assert.assertTrue;

@Paramixel.Factory
public static Action factory() {
    return scope("root")
        .body(sequence("tests")
            .child(Step.of("step-demo", ctx -> {}))
            .child(parallel("parallel-demo")
                .child(Step.of("branch-1", ctx -> {}))
                .child(Step.of("branch-2", ctx -> {}))
                .child(Step.of("branch-3", ctx -> {}))
                .build())
            .child(repeat("repeat-demo")
                .body(Step.of("repeated", ctx -> {}))
                .iterations(3)
                .build())
            .child(timeout("timeout-demo")
                .timeout(Duration.ofSeconds(1))
                .body(Step.of("within-timeout", ctx -> {}))
                .build())
            .child(assertTrue("true-is-true", true))
            .build())
        .build();
}
```

See [Named Builders](../api/named-builders) for the complete method reference.


## Kafka compatibility matrix

Testcontainers examples show where Paramixel's execution-tree model is useful. A Kafka compatibility test is not just a parameterized method over Kafka versions. The tree can run version-specific environments in parallel, wrap each environment in its own setup/body/teardown lifecycle, run nested producer/consumer checks inside each environment, and wrap the whole matrix with global hooks.

```text
before[sequence]: global hooks
body[parallel]: Kafka compatibility matrix
  ├─ instance: Kafka 3.x environment
  │  ├─ before[step]: start environment
  │  ├─ body[sequence]: producer/consumer checks
  │  └─ after[step]: stop environment
  └─ instance: Kafka 4.x environment
     ├─ before[step]: start environment
     ├─ body[sequence]: producer/consumer checks
     └─ after[step]: stop environment
after[sequence]: global cleanup
```

This is the kind of execution tree Paramixel is designed to make explicit: the lifecycle and parallelism are part of the test model, not incidental behavior around a parameterized invocation. See the repository's Kafka Testcontainers examples under `examples/src/main/java/examples/testcontainers/kafka`.
