---
title: First Test
description: Write and run your first Paramixel test.
---

# First Test

This guide walks you through writing and running a complete Paramixel test.

## Write a test factory

Create a class with a `@Paramixel.Factory` method that returns an `Action`:

```java
package com.example;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Step;

public class FirstTest {

    public static void main(String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action<?> factory() {
        return Lifecycle.of("FirstTest")
                .child(greet())
                .child(verify())
                .resolve();
    }

    private static Action<?> greet() {
        return Step.of("greet", instance -> {
            System.out.println("Hello from Paramixel!");
        });
    }

    private static Action<?> verify() {
        return Step.of("verify", instance -> {
            assert 1 + 1 == 2 : "basic math works";
        });
    }
}
```

Key points:

- `@Paramixel.Factory` marks the method for classpath discovery.
- Each action is created by a `private static` method for IDE navigability.
- `Lifecycle.of(name)` creates a spec for a composite with before/body/after support.
- `Step.of(name, consumer)` creates a leaf action from a callback.

## Run with Maven

```bash
./mvnw test
```

The Maven plugin discovers `@Paramixel.Factory` methods on the test classpath and executes them.

## Run with Gradle

```bash
./gradlew paramixelTest
```

## Run programmatically

```java
import org.paramixel.api.Runner;
import org.paramixel.api.Result;
import org.paramixel.api.Status;

Result result = Runner.defaultRunner().run(factory());

if (result.status().isPassed()) {
    System.out.println("All tests passed!");
}
```

## Check the output

Paramixel prints a tree summary to the console:

```
PASSED FirstTest (12ms)
├─ PASSED greet (1ms)
└─ PASSED verify (0ms)
```

## Add a failing test

Throw an exception inside a `Step` callback to produce a `FAILED` status:

```java
private static Action<?> failingTest() {
    return Step.of("failing", instance -> {
        throw new AssertionError("expected failure");
    });
}
```

Run again and observe the failure in the output:

```
FAILED FirstTest (15ms)
├─ PASSED greet (1ms)
└─ FAILED failing (2ms) — AssertionError: expected failure
```

## Skip and abort

Use `SkipException` and `AbortedException` for conditional test outcomes:

```java
import org.paramixel.api.exception.SkipException;
import org.paramixel.api.exception.AbortedException;

private static Action<?> skippedTest() {
    return Step.of("skipped", instance -> {
        throw new SkipException("precondition not met");
    });
}

private static Action<?> abortedTest() {
    return Step.of("aborted", instance -> {
        throw new AbortedException("environment unavailable");
    });
}
```

## Next steps

- [Project Setup](./project-setup)
- [Actions](../core-concepts/actions)
- [Discovery](../core-concepts/discovery)
