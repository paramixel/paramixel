---
title: Direct
description: Execute a callback as an action.
---

# Direct

`Direct` is the simplest built-in action.

## Factory

```java
Direct.of(String name, Direct.Executable executable)
```

The callback shape is:

```java
void execute(Context context) throws Throwable
```

## Example

```java
import org.paramixel.core.action.Direct;

Direct step = Direct.of("create user", context -> {
    String baseUrl = context.getConfiguration().get("service.url");
    // test logic here
});
```

## Result behavior

- normal return -> `PASS`
- `throw SkipException.skip(...)` -> `SKIP`
- `throw FailException.fail(...)` -> `FAIL`
- any other thrown exception -> listener gets `actionThrowable(...)`, then result becomes `FAIL`
- `throw Error` -> propagates immediately (not caught, no result set)
