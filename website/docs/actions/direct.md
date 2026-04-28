---
id: direct
title: Direct
description: Leaf action that executes a callback directly
---

# Direct

`Direct` is a leaf action that executes an `Executable` callback.

## Creating Direct Actions

```java
import org.paramixel.core.action.Direct;

Action action = Direct.of("my action", context -> {
});
```

`Executable` is a functional interface:

```java
@FunctionalInterface
public interface Executable {
    void execute(Context context) throws Throwable;
}
```

## Key Behavior

- Executes the provided callback synchronously
- Returns `PASS` if the callback completes without throwing
- Returns `FAIL` if the callback throws an exception
- Returns `SKIP` if the callback throws `SkipException`

## Example: Simple Test

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Direct.of("test addition", context -> assertThat(2 + 2).isEqualTo(4)),
        Direct.of("test subtraction", context -> assertThat(5 - 3).isEqualTo(2)));
}
```

## See Also

- [Action Composition](../usage/action-composition) - How to compose Direct with other actions
- [Noop](./noop) - Leaf action that does nothing
- [Context](../usage/context) - Accessing runtime state
