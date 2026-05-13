---
title: Direct
description: Run a callback as an action.
---

# Direct

`Direct` runs a callback.

```java
private static Action step() {
    return Direct.builder("step")
            .runnable(context -> {})
            .build();
}
```

Throw `SkipException` to skip or `FailException` to fail intentionally. Unexpected throwables are reported and converted to failures, except unrecoverable errors.

`Direct` is the simplest built-in action.

## Builder

```java
Direct.builder(String name)
        .runnable(Direct.ThrowableRunnable runnable)
        .build()
```

The callback shape is:

```java
void run(Context context) throws Throwable
```

## Example

```java
public class MyTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action createUser = createUser();

        return Container.builder("MyTest")
                .child(createUser)
                .build();
    }

    private static Action createUser() {
        return Direct.builder("create user")
                .runnable(context -> {
                    String baseUrl = context.getConfiguration().get("service.url");
                    // test logic here
                })
                .build();
    }
}
```

## Result behavior

- normal return -> `PASS`
- `throw SkipException.skip(...)` -> `SKIP`
- `throw FailException.fail(...)` -> `FAIL`
- any other thrown exception -> listener gets `actionThrowable(...)`, then result becomes `FAIL`
- `throw OutOfMemoryError` or `StackOverflowError` -> propagates immediately
- other `Error` subclasses -> listener gets `actionThrowable(...)`, then result becomes `FAIL`

## Exceptions

`FailException` and `SkipException` live in `org.paramixel.core.exception`:

```java
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;
```
