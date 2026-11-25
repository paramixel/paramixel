---
title: "Quick Start: Your First Definition"
description: Write and run a minimal Paramixel task definition.
---

# Quick Start: Your First Definition

Create a class with a public static factory method annotated with `@Paramixel.TaskFactory`. The method returns a `Task`, a
builder, or `null`. Returning `null` is an explicit skip with a recorded reason.

```java
package com.example;

import static org.paramixel.api.exception.FailException.fail;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Sequential;
import org.paramixel.api.SequentialBuilder;

public final class LoginSmokeTest {

    private static final String ID_OPEN = "openLoginPage";
    private static final String ID_SUBMIT = "submitCredentials";
    private static final String ID_ASSERT = "assertSignedIn";

    @Paramixel.TaskFactory
    public static SequentialBuilder factory() {
        return Sequential.builder(LoginSmokeTest.class, "login smoke")
                .step(ID_OPEN)
                .step(ID_SUBMIT)
                .step(ID_ASSERT);
    }

    @Paramixel.Id(ID_OPEN)
    public static void openLoginPage(final Context context) {}

    @Paramixel.Id(ID_SUBMIT)
    public static void submitCredentials(final Context context) {}

    @Paramixel.Id(ID_ASSERT)
    public static void assertSignedIn(final Context context) {
        if (!isSignedIn()) {
            throw fail("user is not signed in");
        }
    }

    private static boolean isSignedIn() {
        return true;
    }
}
```

## Run it

```bash
./mvnw test
```

The Maven plugin discovers the factory, builds and validates the definition, then executes it. The run fails the build
if the result's exit code is non-zero.

## Run it programmatically

```java
try (Runner runner = Runner.builder().build()) {
    Result result = runner.run(LoginSmokeTest.factory());
    System.out.println(result.status() + " exit=" + result.exitCode());
}
```

`Runner.run(Task)` performs no discovery and runs no `@BeforeAll`/`@AfterAll` hooks: the supplied definition is the whole
run. `Runner.run(TaskBuilder<?>)` builds what you authored and runs it, which is why a `main` method can pass a factory's
result directly.

## Add a fixture

```java
private static final String FIXTURE = "browser";

@Paramixel.TaskFactory
public static SequentialBuilder factory() {
    return Sequential.builder(LoginSmokeTest.class, "browser")
            .before("launch")
            .step("openLoginPage")
            .step("submitCredentials")
            .after("close");
}

@Paramixel.Id("launch")
public static void launch(final Context context) {
    context.put(FIXTURE, Browser.launch());
}

@Paramixel.Id("openLoginPage")
public static void openLoginPage(final Context context) {
    context.require(FIXTURE, Browser.class).openLoginPage();
}

@Paramixel.Id("close")
public static void close(final Context context) {
    context.find(FIXTURE, Browser.class).ifPresent(Browser::close);
}
```

The fixture is a plain object stored in the context scope. `find` in the `after` hook means a failed launch cannot mask
the original failure, and the framework never closes anything itself.

## Next

- [Tasks](../core-concepts/tasks) — the definition model.
- [Lifecycle and scope](../core-concepts/elements) — phases and finalization.
- [Quick reference](../guides/quick-reference) — the full authoring surface.
