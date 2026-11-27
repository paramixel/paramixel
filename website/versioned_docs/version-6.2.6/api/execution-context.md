---
title: Context
description: Runtime services available to actions.
---

# Context

`Context` is passed to `ContextConsumer` functions during step execution. It is the action-facing view of the current run, providing access to configuration and fixture instances.

```java
package org.paramixel.api;

public interface Context {
    Configuration configuration();
    <T> Optional<T> instance(Class<T> type);
    default <T> T requireInstance(Class<T> type);
    static <T> ContextConsumer withInstance(
            Class<T> type,
            InstanceConsumer<? super T> consumer);
}
```

## Configuration access

```java
var parallelism = context.configuration()
        .getInteger("paramixel.parallelism")
        .orElse(1);
```

## Fixture access

`Instance` actions make fixture objects available through `Context`.

```java
import org.paramixel.api.Context;
import static org.paramixel.api.action.Step.step;

var verifyLogin = step(
        "verify login",
        Context.withInstance(Browser.class, browser -> browser.open("/login")));
```

Use `context.instance(Type.class)` when a fixture is optional. Use `context.requireInstance(Type.class)` or `Context.withInstance(...)` when the fixture is required.

## Outcomes

Actions communicate outcomes by throwing Paramixel outcome exceptions such as `FailException`, `SkipException`, or `AbortedException`. The scheduler catches these and maps them to terminal `Status` values on the descriptor.
