---
title: Paramixel
description: Action-based Java testing with composable action trees.
slug: /
---

# Paramixel

Paramixel is a Java 17+ test framework built around executable `Action` trees.

Instead of describing tests with many framework-specific annotations, you build them with plain Java using actions like `Sequential`, `Parallel`, `Lifecycle`, and `Direct`.

## Core ideas

- Tests are trees of `Action` objects.
- Discovery is optional: `@Paramixel.ActionFactory` marks a `public static` no-arg factory method.
- `Runner.run(action)` executes an action tree and returns `void`.
- Read outcomes from `action.getResult()` after execution.
- Runtime state is passed through `Context`.

## Minimal example

```java
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;

public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Sequential.of(
                "MyTest",
                Direct.of("step 1", context -> {}),
                Direct.of("step 2", context -> {}));
    }
}
```

## Running tests

### Programmatically

```java
import org.paramixel.core.Runner;

Action action = MyTest.actionFactory();
Runner.builder().build().run(action);

if (action.getResult().getStatus().isFailure()) {
    throw new IllegalStateException("test failed");
}
```

### From the console

```java
import org.paramixel.core.ConsoleRunner;

ConsoleRunner.runAndExit(MyTest.actionFactory());
```

### With Maven

Use the Paramixel Maven plugin. It discovers `@Paramixel.ActionFactory` methods on the test classpath and runs them during the `test` phase.

## Built-in actions

- `Direct` - run a callback
- `Sequential` - run all children in order
- `StrictSequential` - stop on first child failure; skip the rest
- `RandomSequential` - run all children in shuffled order
- `StrictRandomSequential` - shuffled fail-fast execution
- `Parallel` - run children concurrently
- `Lifecycle` - `before`, `main`, `after`
- `Noop` - do nothing and pass

## Configuration

Core configuration is loaded from:

1. `paramixel.properties` on the classpath
2. JVM system properties
3. programmatic `Runner.builder().configuration(...)` overrides, when used

The main built-in key is `paramixel.parallelism`.

## Next steps

- [Quick Start](quick-start)
- [Configuration](configuration)
- [Action composition](usage/action-composition)
- [Discovery](usage/discovery)
- [Maven plugin](usage/maven-plugin)
- [API reference](api/intro)
