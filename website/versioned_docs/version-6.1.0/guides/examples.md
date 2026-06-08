---
title: Examples
description: Source-backed example patterns.
---

# Examples

The repository's examples live under `examples/src/main/java`. They demonstrate factory discovery, annotations, lifecycle composition, retry support, and Testcontainers integration.

## Factory with tags

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

@Paramixel.Factory
@Paramixel.Tag("smoke")
@Paramixel.Tag("critical")
public static Action smoke() {
    return Sequence.builder("smoke")
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
import org.paramixel.api.action.Sequence;

final class LoginFixture {
    @Paramixel.Id("open")
    public void open() {}

    @Paramixel.Id("submit")
    public void submit() {}
}

var resolver = AnnotationResolver.create(LoginFixture.class);
Action fixture = Instance.builder(LoginFixture.class)
        .body(Sequence.builder("login")
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
    return Sequence.builder("disabled").build();
}
```

## Kafka compatibility matrix

Testcontainers examples show where Paramixel's execution-graph model is useful. A Kafka compatibility test is not just a parameterized method over Kafka versions. The graph can run version-specific environments in parallel, wrap each environment in its own setup/body/teardown lifecycle, run nested producer/consumer checks inside each environment, and wrap the whole matrix with global hooks.

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

This is the kind of topology Paramixel is designed to make explicit: the lifecycle and parallelism are part of the test model, not incidental behavior around a parameterized invocation. See the repository's Kafka Testcontainers examples under `examples/src/main/java/examples/testcontainers/kafka`.
