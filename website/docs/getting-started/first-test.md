---
title: "Quick Start: Your First Definition"
description: Write and run a minimal Paramixel task definition.
---

# Quick Start: Your First Definition

Create a class with a public static factory method annotated with `@Paramixel.Factory`. The method returns a `Task`, a
builder, or `null`. Returning `null` is an explicit skip with a recorded reason.

```java
package com.example;

import static org.paramixel.api.Tasks.sequential;
import static org.paramixel.api.exception.FailException.fail;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Task;

public final class LoginSmokeTest {

    @Paramixel.Factory
    public static Task factory() {
        return sequential("login smoke")
                .step("open login page", LoginSmokeTest::openLoginPage)
                .step("submit credentials", LoginSmokeTest::submitCredentials)
                .step("user is signed in", () -> {
                    if (!isSignedIn()) {
                        throw fail("user is not signed in");
                    }
                })
                .build();
    }

    private static void openLoginPage() {}

    private static void submitCredentials() {}

    private static boolean isSignedIn() {
        return true;
    }
}
```

## Run it

```bash
./mvnw test
```

The Maven plugin discovers the factory, compiles and validates the definition, then executes it. The run fails the build
if the result's exit code is non-zero.

## Run it programmatically

```java
try (Runner runner = Runner.builder().build()) {
    Result result = runner.run(LoginSmokeTest.factory());
    System.out.println(result.status() + " exit=" + result.exitCode());
}
```

`Runner.run(Task)` performs no discovery and runs no `@BeforeAll`/`@AfterAll` hooks: the supplied definition is the whole
run.

## Add a fixture

```java
private static final String FIXTURE = "browser";

@Paramixel.Factory
public static Task factory() {
    return sequential(FirstTest.class, "browser")
            .before("launch")
            .step("openLoginPage")
            .step("submitCredentials")
            .after("close")
            .build();
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

- [Tasks](../core-concepts/actions) — the definition model.
- [Lifecycle and scope](../core-concepts/elements) — phases and finalization.
- [Quick reference](../guides/quick-reference) — the full authoring surface.
