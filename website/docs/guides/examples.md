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
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

@Paramixel.Factory
@Paramixel.Tag("smoke")
@Paramixel.Tag("critical")
public static Action smoke() {
    return sequential("smoke")
            .child(step("arrange", ctx -> arrange()))
            .child(step("act", ctx -> act()))
            .child(step("assert", ctx -> assertResult()))
            .build();
}
```

## Typed fixture pattern

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

final class LoginFixture {
    void open() {}
    void submit() {}
}

@Paramixel.Factory
public static Action factory() {
    return of(LoginFixture.class)
            .before("open()", LoginFixture::open)
            .body(sequential("login")
                    .child(step("submit()", LoginFixture.class, LoginFixture::submit))
                    .build())
            .build();
}
```

The fixture type appears once, in `of(...)`; every step names only the method it calls, and the compile step
rejects a step whose fixture is missing.

## Disabled factory

```java
import static org.paramixel.api.action.Sequential.sequential;

@Paramixel.Factory
@Paramixel.Disabled("under investigation")
public static Action temporarilyDisabled() {
    return sequential("disabled").build();
}
```

## Named builders

The `ActionsExample` in the repository demonstrates the named builder factories:

```java
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Timeout.timeout;

@Paramixel.Factory
public static Action factory() {
    return scope("root")
        .body(sequential("tests")
            .child(step("step-demo", ctx -> {}))
            .child(parallel("parallel-demo", "examples")
                .child(step("branch-1", ctx -> {}))
                .child(step("branch-2", ctx -> {}))
                .child(step("branch-3", ctx -> {}))
                .build())
            .child(repeat("repeat-demo")
                .body(step("repeated", ctx -> {}))
                .iterations(3)
                .build())
            .child(timeout("timeout-demo")
                .timeout(Duration.ofSeconds(1))
                .body(step("within-timeout", ctx -> {}))
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
