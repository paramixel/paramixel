---
title: Context
description: Read-only runtime services available to steps.
---

# Context

`Context` is passed to step consumers during execution. It is the read-only, action-facing view of the current run:

```java
package org.paramixel.api;

public interface Context {
    Configuration configuration();
    Descriptor descriptor();
    <T> Optional<T> instance(Class<T> type);
    default <T> T requireInstance(Class<T> type);
}
```

`Context` is not for implementation by users: the framework supplies the only implementation, and there is no
public `bind`. Fixtures are provided only by an `Instance` in the tree, which is what lets the compile step
validate every typed step before any user code runs.

## Configuration access

```java
var parallelism = context.configuration().getInteger(Configuration.RUNNER_PARALLELISM).orElse(1);
```

## Descriptor access

```java
step("log my path", context -> log.info("running " + context.descriptor().path()));
```

`Descriptor.path()` is the display-name path from the run root, and `Descriptor.displayName()` is the action name.

## Fixture access

A typed step receives its fixture directly; the type appears once, in `Instance.of`:

```java
Instance.of("browser", Browser.class, () -> new Browser())
        .before("start", Browser::start)
        .body(step("open /login", Browser.class, browser -> browser.open("/login")))
        .after("stop", Browser::stop);
```

Inside an untyped step, or when a fixture is optional, use `context.instance(Type.class)`; use
`context.requireInstance(Type.class)` when it is required.

## Outcomes

Steps communicate outcomes by throwing Paramixel outcome exceptions (`FailException`, `SkipException`,
`AbortException`), or by using the helpers with `throw fail("…")`. The engine maps them to terminal `Status` values
on the descriptor.
