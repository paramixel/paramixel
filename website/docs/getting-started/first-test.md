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

## Add an owned fixture

```java
@Paramixel.Factory
public static Task factory() {
    return forInstance("browser", Browser::launch)
            .before(Browser::start)
            .step("open login page", Browser::openLoginPage)
            .step("submit credentials", Browser::submitCredentials)
            .after(Browser::close)
            .build();
}
```

The class token appears once, in the supplier. Every step receives the instance directly.

## Next

- [Tasks](../core-concepts/actions) — the definition model.
- [Lifecycle and scope](../core-concepts/elements) — phases and finalization.
- [Quick reference](../guides/quick-reference) — the full authoring surface.
