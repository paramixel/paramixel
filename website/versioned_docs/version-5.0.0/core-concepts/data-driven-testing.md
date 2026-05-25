---
title: Data-Driven Testing
description: Run the same test logic against multiple inputs.
---

# Data-Driven Testing

Data-driven testing runs the same test logic against multiple inputs. In Paramixel, this is achieved with the spec + loop pattern.

## Spec + loop pattern

Create a composite spec, iterate over arguments, and add each as a child:

```java
import java.util.List;
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

public class DataDrivenTest {

    @Paramixel.Factory
    public static Action<?> factory() {
        var spec = Sequential.of("DataDrivenTest")
                .independent();
        for (String arg : List.of("alpha", "beta", "gamma")) {
            spec.child(argument(arg));
        }
        return spec.resolve();
    }

    private static Action<?> argument(String arg) {
        return Step.of(arg, ctx -> {
            System.out.println("Testing: " + arg);
        });
    }
}
```

## Sequential arguments

Use `Sequential` with `independent()` to run every argument regardless of earlier results:

```java
Sequential.of("sequential-args")
        .independent()
        .child(argument("case-1"))
        .child(argument("case-2"))
        .child(argument("case-3"))
        .resolve();
```

Use the default `dependent()` for fail-fast behavior that stops after the first failure.

## Parallel arguments

Use `Parallel` to run arguments concurrently:

```java
Parallel.of("parallel-args")
        .parallelism(4)
        .child(argument("case-1"))
        .child(argument("case-2"))
        .child(argument("case-3"))
        .resolve();
```

## Per-argument lifecycle

Wrap each argument in a `Lifecycle` or `Instance` for per-argument setup and teardown:

### With Lifecycle

```java
private static Action<?> argument(String arg) {
    return Lifecycle.of(arg)
            .before(Step.of("setup", ctx -> { /* setup for arg */ }))
            .child(Step.of("test", ctx -> { /* test with arg */ }))
            .after(Step.of("teardown", ctx -> { /* cleanup for arg */ }))
            .resolve();
}
```

### With Instance

```java
private static Action<?> argument(TestEnvironment env) {
    return Instance.of(env.name(), () -> env)
            .child("initialize", TestEnvironment::initialize)
            .child("test", TestEnvironment::runTest)
            .resolve();
}
```

## Argument types

Any Java type works as argument data.

### Primitive types

```java
for (int value : new int[]{1, 2, 3}) {
    spec.child(Step.of("int-" + value, ctx -> {
        assertThat(value).isPositive();
    }));
}
```

### Records

```java
record TestCase(String name, int input, int expected) {}

for (TestCase tc : List.of(
        new TestCase("positive", 5, 25),
        new TestCase("negative", -3, 9))) {
    spec.child(Step.of(tc.name(), ctx -> {
        assertThat(tc.input() * tc.input()).isEqualTo(tc.expected());
    }));
}
```

### Enums

```java
for (Environment env : Environment.values()) {
    spec.child(argument(env));
}
```

## Dynamic spec with loop

When the argument list is dynamic:

```java
@Paramixel.Factory
public static Action<?> factory() {
    var spec = Parallel.of("dynamic-args")
            .parallelism(2);
    for (TestEnvironment env : TestEnvironment.createEnvironments()) {
        spec.child(argument(env));
    }
    return spec.resolve();
}
```

## Full example with Testcontainers

```java
@Paramixel.Factory
public static Action<?> factory() {
    var spec = Parallel.of("container-tests")
            .parallelism(2);
    for (String image : List.of("nginx:latest", "nginx:stable")) {
        spec.child(containerTest(image));
    }
    return spec.resolve();
}

private static Action<?> containerTest(String image) {
    return Lifecycle.of(image)
            .before(Step.of("start", ctx -> { startContainer(image); }))
            .child(Step.of("verify", ctx -> { verifyContainer(); }))
            .after(Step.of("stop", ctx -> { stopContainer(); }))
            .resolve();
}
```

See [Best Practices](../guides/best-practices) for more Testcontainers patterns.
