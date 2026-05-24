---
title: Examples
description: Example projects and patterns using Paramixel.
---

# Examples

The Paramixel repository includes a comprehensive `examples/` module that demonstrates every framework feature. Each example is a complete, runnable test class.

## Running the examples

The examples live under `examples/src/main/java/` and are executed by the Paramixel Maven plugin during the `test` phase.

```bash
./mvnw test -pl examples
```

To run a specific package of examples from an IDE, use the `__ParamixelRunner__` convention (see below).

## The `__ParamixelRunner__` convention

A `__ParamixelRunner__` class is a console entry point that runs all tests in its package and sub-packages. The double-underscore prefix sorts the file to the top of the package in IDE file trees.

```java
package examples;

import org.paramixel.api.Runner;
import org.paramixel.api.selector.Selector;

public class __ParamixelRunner__ {
    public static void main(String[] args) {
        Runner.defaultRunner().runAndExit(
            Selector.packageTreeOf(__ParamixelRunner__.class));
    }
}
```

`Selector.packageTreeOf(Class)` selects all `@Paramixel.Factory` methods in the specified package and all sub-packages. This makes it easy to run a subset of tests from an IDE.

### Package runners in the examples module

| Runner | Scope |
| --- | --- |
| `examples.__ParamixelRunner__` | All example tests (includes `repeat/` sub-package) |
| `examples.annotation.__ParamixelRunner__` | Annotation examples + `tags/` sub-package |
| `examples.annotation.tags.__ParamixelRunner__` | Tag filtering examples |
| `examples.argument.__ParamixelRunner__` | Mixed argument examples |
| `examples.lifecycle.__ParamixelRunner__` | All lifecycle pattern examples |
| `examples.retry.__ParamixelRunner__` | Retry integration examples |
| `examples.testcontainers.__ParamixelRunner__` | All Testcontainers examples (Kafka, MongoDB, Nginx) |

The `examples.repeat` package does not have its own `__ParamixelRunner__` — it is discovered by the root `examples.__ParamixelRunner__` via `Selector.packageTreeOf`.

## Root-level examples

The `examples` root package contains additional feature demonstrations:

| Example | Feature |
| --- | --- |
| `NestedTest` | Nested `Parallel` + `Lifecycle` composition |
| `NestedParallelDeadlockTest` | Nested `Parallel` with cooperative work-stealing |
| `ParallelInstanceTest` | `Parallel` + `Instance` composition |
| `DelayTest` | `Delay` action with fixed and random durations |
| `AssertTest` | `AssertTrue` and `AssertFalse` actions |
| `CustomActionTest` | Custom `Action` implementation |

## Programmatic vs annotation-based patterns

Every example concept has two parallel implementations:

| Programmatic | Annotation-based (`AnnotationResolver`) |
| --- | --- |
| Method references on the fixture class | `@Paramixel.Id` methods resolved by `AnnotationResolver` |
| `Instance.of("name", MyTest::new).child("method", MyTest::method)` | `Instance.of(MyTest.class).child(resolver.byId("method"))` |

### Programmatic example

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Step;

public class SimpleTest {

    public static void main(String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action<?> factory() {
        return Instance.of("SimpleTest", SimpleTest::new)
                .child("testGreeting", SimpleTest::testGreeting)
                .resolve();
    }

    public void testGreeting() {
        System.out.println("Hello from Paramixel!");
    }
}
```

### Annotation-based example

```java
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;

public class AnnotationSimpleTest {

    public static void main(String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action<?> factory() {
        var resolver = AnnotationResolver.create(AnnotationSimpleTest.class);
        return Instance.of("AnnotationSimpleTest", AnnotationSimpleTest::new)
                .child(resolver.byId("testGreeting"))
                .resolve();
    }

    @Paramixel.Id("testGreeting")
    public void testGreeting() {
        System.out.println("Hello from Paramixel!");
    }
}
```

## Lifecycle patterns

The examples module demonstrates all lifecycle levels:

| Example | Pattern |
| --- | --- |
| `SimpleTest` / `AnnotationSimpleTest` | Basic `Instance` with `AutoCloseable` |
| `TestLifecycleTest` / `AnnotationTestLifecycleTest` | `Instance` + test methods |
| `SetUpTearDownLifecycleTest` / `AnnotationSetUpTearDownLifecycleTest` | `Lifecycle` with before/after |
| `FullLifecycleTest` / `AnnotationFullLifecycleTest` | `Static` (class setup) + `Instance` + `Lifecycle` (method setup/teardown) |

### Full lifecycle example

```java
@Paramixel.Factory
public static Action<?> factory() {
    return Static.of("FullLifecycleTest")
            .before("setUpClass", FullLifecycleTest::setUpClass)
            .child(Instance.of("FullLifecycleTest", FullLifecycleTest::new)
                    .child(Lifecycle.of("testMethod")
                            .before("setUp", FullLifecycleTest::setUp)
                            .child("testMethod", FullLifecycleTest::testMethod)
                            .after("tearDown", FullLifecycleTest::tearDown)
                            .resolve())
                    .resolve())
            .after("tearDownClass", FullLifecycleTest::tearDownClass)
            .resolve();
}
```

## Dependent vs independent arguments

| Example | Pattern |
| --- | --- |
| `DependentArgumentTest` | Sequential dependent — stops after first failure |
| `IndependentArgumentTest` | Sequential independent — runs all regardless of failures |
| `ParallelArgumentTest` | Parallel — runs all concurrently |
| `MixedArgumentTest` | Mix of dependent and independent children |

## Tag-based filtering

Use `@Paramixel.Tag` to categorize tests and `Selector.tagRegex()` or `paramixel.match.tag.regex` to filter:

```java
@Paramixel.Factory
@Paramixel.Tag("smoke")
@Paramixel.Tag("critical")
public static Action<?> factory() { /* ... */ }
```

```bash
./mvnw test -Dparamixel.match.tag.regex=smoke
```

## Disabled tests

Use `@Paramixel.Disabled` to exclude a factory from discovery:

```java
@Paramixel.Factory
@Paramixel.Disabled("under investigation")
public static Action<?> factory() { /* ... */ }
```

## Testcontainers integration

The examples module includes Testcontainers-based integration tests for Kafka, MongoDB, and Nginx. Each container type follows a test + environment pair pattern.

### Pattern

| Class | Purpose |
| --- | --- |
| `XxxTest` / `AnnotationXxxTest` | Paramixel test class with setUp/test/tearDown methods |
| `XxxTestEnvironment` | Manages Testcontainers lifecycle (create, start, stop) |

### Example: Nginx with Testcontainers

```java
@Paramixel.Factory
public static Action<?> factory() {
    var spec = Parallel.of("NginxTest")
            .parallelism(2);
    for (String image : Resource.load(NginxTest.class, "docker-images.txt")) {
        spec.child(containerTest(image));
    }
    return spec.resolve();
}

private static Action<?> containerTest(String image) {
    return Instance.of(image, () -> new NginxTestEnvironment(image))
            .child("setUp", NginxTestEnvironment::setUp)
            .child("testHttp", NginxTestEnvironment::testHttp)
            .child("tearDown", NginxTestEnvironment::tearDown)
            .resolve();
}
```

The `NginxTestEnvironment` class manages the `NginxContainer` lifecycle, including Docker network creation and cleanup.

## Next steps

- [Getting Started](../getting-started/introduction)
- [Core Concepts](../core-concepts/actions)
- [Testcontainers](https://java.testcontainers.org/)
