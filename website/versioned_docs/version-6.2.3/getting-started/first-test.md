---
title: "Quick Start: Your First Execution Tree"
description: Write and run a minimal Paramixel execution tree.
---

# Quick Start: Your First Execution Tree

Create a class with a public static factory method annotated with `@Paramixel.Factory`. The method returns an `Action`, a `Builder`, or `null`. Returning `null` means the factory is represented as a skipped action outcome.

```java
package com.example;

import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Assert.assertTrue;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

public final class LoginSmokeTest {
    @Paramixel.Factory
    public static Action test() {
        return sequential("login smoke")
                .child(step("open login page", ctx -> openLoginPage()))
                .child(step("submit credentials", ctx -> submitCredentials()))
                .child(assertTrue("user is signed in", LoginSmokeTest::isSignedIn))
                .build();
    }

    private static void openLoginPage() {}

    private static void submitCredentials() {}

    private static boolean isSignedIn() {
        return true;
    }
}
```

This factory builds this execution tree:

```text
LoginSmokeTest
└── login smoke
    ├── open login page
    ├── submit credentials
    └── user is signed in
```

Paramixel executes the tree and returns a result tree with the same shape.

Run with Maven:

```bash
mvn test
```

Or invoke the plugin goal directly:

```bash
mvn paramixel:test
```

## Programmatic run

```java
import org.paramixel.api.Runner;

var result = Runner.defaultRunner().run(LoginSmokeTest.test());
if (result.isFailed()) {
    throw new AssertionError("Paramixel run failed");
}
```

The returned `Result` exposes the root `Descriptor` and effective aggregate status.

## Using named builders

The same test can be written with static imports from each action class:

```java
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Assert.assertTrue;
import static org.paramixel.api.action.Step.step;

@Paramixel.Factory
public static Action test() {
    return sequential("login smoke")
        .child(step("open login page", ctx -> openLoginPage()))
        .child(step("submit credentials", ctx -> submitCredentials()))
        .child(assertTrue("user is signed in", LoginSmokeTest::isSignedIn))
        .build();
}
```

For the full method reference, see [Named Builders](../api/named-builders).


## Next steps

- [When to Use Paramixel](when-to-use)
- [Core Concepts](../core-concepts)
- [Test Shapes](../test-shapes)
