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
- `Runner.run(action)` executes an action tree and returns a `Result`.
- Read outcomes from the returned `Result` tree after execution.
- Runtime state is passed through `Context` and its `Store`.

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
- `Sequential` - run all children in order
- `DependentSequential` - stop on first child failure; skip the rest
- `RandomSequential` - run all children in shuffled order
- `DependentRandomSequential` - shuffled fail-fast execution
- `Parallel` - run children concurrently
- `Lifecycle` - `before`, `main`, `after`
- `Noop` - do nothing and pass

## Configuration

Core configuration is loaded from:

1. `paramixel.properties` on the classpath
2. JVM system properties
3. programmatic `Runner.builder().configuration(...)` overrides, when used

Built-in keys include `paramixel.parallelism`, `paramixel.match.package`, `paramixel.match.class`, and `paramixel.match.tag`.

Use `@Paramixel.Tag` to tag action factories for selective discovery:

```java
@Paramixel.ActionFactory
@Paramixel.Tag("smoke")
public static Action smokeTests() { /* ... */ }
```

## Next steps

- [Quick Start](quick-start)
- [Configuration](configuration)
- [Action composition](usage/action-composition)
- [Discovery](usage/discovery)
- [Maven plugin](usage/maven-plugin)
- [API reference](api/intro)