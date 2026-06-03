---
title: Discovery
description: How Paramixel finds factory methods.
---

# Discovery

Classpath discovery finds public static methods annotated with `@Paramixel.Factory`. A factory returns an `Action`, a `Builder`, or `null`.

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

public final class CheckoutTest {
    @Paramixel.Factory
    @Paramixel.Tag("smoke")
    public static Action checkout() {
        return Sequence.builder("checkout")
                .child(Step.of("add item", ctx -> addItem()))
                .child(Step.of("pay", ctx -> pay()))
                .build();
    }
}
```

## Discovery annotations

- `@Paramixel.Factory` marks a factory method returning `Action`, `Builder`, or `null`.
- `@Paramixel.Disabled` excludes a factory from discovery.
- `@Paramixel.Tag` is repeatable and supports tag filtering.
- `@Paramixel.Priority` orders discovered factory classes before the root action is built.
- `@Paramixel.Id` is used by `AnnotationResolver`, not factory discovery.

## Filtering

Use configuration keys or selectors:

```properties
paramixel.match.package.regex=com\.example\.smoke
paramixel.match.class.regex=Checkout
paramixel.match.tag.regex=smoke|critical
```

The regex filters use Java `Pattern.find()` semantics.
