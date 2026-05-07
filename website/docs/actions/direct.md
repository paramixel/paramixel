---
title: Direct
description: Execute a callback as an action.
---

# Direct

`Direct` executes a callback. In 3.x it is builder-only so context mode is explicit and discoverable.

```java
private static Action step() {
    return Direct.builder("step")
            .execute(context -> {})
            .build();
}
```

To share the context received from the parent action:

```java
private static Action step() {
    return Direct.builder("step")
            .contextMode(Action.ContextMode.SHARED)
            .execute(context -> {})
            .build();
}
```

Throw `SkipException` to skip or `FailException` to fail intentionally. Other non-`Error` throwables are reported and converted to failures.

`Direct` is the simplest built-in action.

## Builder

```java
Direct.builder(String name)
        .contextMode(Action.ContextMode contextMode) // optional
        .execute(Direct.Executable executable)
        .build()
```

The callback shape is:

```java
void execute(Context context) throws Throwable
```

## Example

```java
public class MyTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action createUser = createUser();

        return Container.builder("MyTest")
                .child(createUser)
                .build();
    }

    private static Action createUser() {
        return Direct.builder("create user")
                .execute(context -> {
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
- `throw Error` -> propagates immediately (not caught, no result set)

## Exceptions

`FailException` and `SkipException` live in `org.paramixel.core.exception`:

```java
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;
```
