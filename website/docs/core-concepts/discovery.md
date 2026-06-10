---
title: Discovery
description: How Paramixel finds factory methods.
---

# Discovery

Classpath discovery finds public static methods annotated with `@Paramixel.Factory`. A factory returns an `Action`, a `Builder`, or `null`.

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

public final class CheckoutTest {
    @Paramixel.Factory
    @Paramixel.Tag("smoke")
    public static Action checkout() {
        return Sequential.sequential("checkout")
                .child(Step.of("add item", ctx -> addItem()))
                .child(Step.of("pay", ctx -> pay()))
                .build();
    }
}
```

## Discovery annotations

- `@Paramixel.Factory` marks a factory method returning `Action`, `Builder`, or `null`.
- `@Paramixel.BeforeAll` marks a runner-level hook that executes once before any discovered test actions.
- `@Paramixel.AfterAll` marks a runner-level hook that executes once after all discovered test actions.
- `@Paramixel.Disabled` excludes a factory or hook from discovery.
- `@Paramixel.Tag` is repeatable and supports tag filtering.
- `@Paramixel.Priority` orders discovered classes before the root action is built.
- `@Paramixel.Id` is used by `AnnotationResolver`, not factory discovery.

## BeforeAll and AfterAll hooks

`@Paramixel.BeforeAll` and `@Paramixel.AfterAll` provide runner-level lifecycle hooks. Use them for setup or teardown that belongs to the whole discovered runner invocation, such as starting shared infrastructure, seeding a shared environment, or cleaning up global resources.

For lifecycle that belongs to one test or one action tree, prefer an explicit [`Scope`](../api/action#composition). A hook is a convenience around the discovered root; conceptually, Paramixel wraps all discovered factory actions in a top-level `Scope` with hook sequences as `before` and `after` actions.

Hooks are `public static` methods returning `Action` or `Builder`, discovered alongside factories during classpath scanning.

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

public final class SetupTeardown {

    @Paramixel.BeforeAll
    public static Action setup() {
        return Sequential.sequential("setup")
                .child(Step.of("start server", ctx -> startServer()))
                .child(Step.of("seed data", ctx -> seedData()))
                .build();
    }

    @Paramixel.AfterAll
    public static Action teardown() {
        return Sequential.sequential("teardown")
                .child(Step.of("clean data", ctx -> cleanData()))
                .child(Step.of("stop server", ctx -> stopServer()))
                .build();
    }
}
```

`@BeforeAll` methods execute once before any discovered test actions. A failing or skipped `@BeforeAll` causes the entire run body (all discovered factory actions) to be skipped. `@AfterAll` methods always execute, regardless of earlier failures.

Multiple `@BeforeAll` methods are ordered by `@Paramixel.Priority` on their declaring class, then by package, class, and method name. Multiple `@AfterAll` methods are ordered in reverse.

## Filtering

Use configuration keys or selectors:

```properties
paramixel.match.package.regex=com\.example\.smoke
paramixel.match.class.regex=Checkout
paramixel.match.tag.regex=smoke|critical
```

The regex filters use Java `Pattern.find()` semantics.
