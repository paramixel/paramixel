---
title: AnnotationResolver
description: Resolve @Paramixel.Id methods into Step actions.
---

# AnnotationResolver

`AnnotationResolver<T>` resolves methods annotated with `@Paramixel.Id` into named `Step` actions.

```java
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;

final class Fixture {
    @Paramixel.Id("open")
    public void open() {}

    @Paramixel.Id("reset")
    public static void reset() {}
}

var resolver = AnnotationResolver.create(Fixture.class);
var open = resolver.byId("open");     // returns Action (Step)
var reset = resolver.staticById("reset");  // returns Action (Step)
```

## Signature rules

Resolved methods must be public, accept no arguments, and return `void`. Instance and static methods are resolved independently. IDs must be unique within the visible class hierarchy for the selected resolution kind.

## Usage with Instance

```java
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Step;

var resolver = AnnotationResolver.create(LoginFixture.class);

Action fixture = Instance.builder(LoginFixture.class)
        .body(Step.of("login", ctx -> {
            ctx.requireInstance(LoginFixture.class).open();
            ctx.requireInstance(LoginFixture.class).submit();
        }))
        .build();
```

Resolved methods are cached per class. Use `AnnotationResolver.clearCache(type)` or `AnnotationResolver.clearAllCache()` when tests dynamically change resolution context.
