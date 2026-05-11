---
title: Paramixel
description: Action-based Java testing with composable action trees.
slug: /
---

# Paramixel

Paramixel is a Java 17+ test framework built around executable `Action` trees.

Instead of describing tests with many framework-specific annotations, you build them with plain Java using actions like `Container`, `Parallel`, `Direct`, and `Noop`.

## Core ideas

- Tests are trees of `Action` objects.
- Discovery is optional: `@Paramixel.ActionFactory` marks a `public static` no-arg factory method.
- `Runner.run(action)` executes an action tree and returns a `Result`.
- Read outcomes from the returned `Result` tree after execution.
- Runtime state is passed through `Context` and its `Store`.

## Minimal example

```java
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action step1 = step1();
        Action step2 = step2();

        return Container.builder("MyTest")
                .child(step1)
                .child(step2)
                .build();
    }

    private static Action step1() {
        return Direct.builder("step 1").execute(context -> {}).build();
    }

    private static Action step2() {
        return Direct.builder("step 2").execute(context -> {}).build();
    }
}
```

## Running tests

### Programmatically

```java
import org.paramixel.core.Runner;
import org.paramixel.core.Factory;

Action action = MyTest.actionFactory();
Result result = Factory.defaultRunner().run(action);

if (result.getStatus().isFailure()) {
    throw new IllegalStateException("test failed");
}
```

### From the console

```java
import org.paramixel.core.Runner;

Runner runner = Runner.builder().build();
int exitCode = runner.runAndReturnExitCode(action);
System.exit(exitCode);
```

### With Maven

Use the Paramixel Maven plugin. It discovers `@Paramixel.ActionFactory` methods on the test classpath and runs them during the `test` phase.

## Built-in actions

- `Direct` - run a callback
- `Container` - run ordered children with setup, teardown, and run policy options
- `Parallel` - run children concurrently
- `Noop` - do nothing and pass

## Configuration

Core configuration is loaded from:

1. `paramixel.properties` on the classpath
2. JVM system properties
3. programmatic `Runner.builder().configuration(...)` overrides, when used

Built-in keys include `paramixel.parallelism`, `paramixel.failureOnSkip`, `paramixel.report.file`, `paramixel.match.package`, `paramixel.match.class`, and `paramixel.match.tag`.

Use `@Paramixel.Tag` to tag action factories for selective discovery:

```java
public class SmokeTests {

    @Paramixel.ActionFactory
    @Paramixel.Tag("smoke")
    public static Action actionFactory() { /* ... */ }
}
```

## Next steps

- [Quick Start](quick-start)
- [Configuration](configuration)
- [Action composition](usage/action-composition)
- [Discovery](usage/discovery)
- [Maven plugin](usage/maven-plugin)
- [API reference](api/intro)
