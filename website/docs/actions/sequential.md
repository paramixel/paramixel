---
id: sequential
title: Sequential
description: Execute child actions one at a time, in order
---

# Sequential

`Sequential` runs child actions one at a time, in order.

## Creating Sequential Actions

```java
import org.paramixel.core.action.Sequential;

Action action = Sequential.of("my sequential", List.of(
    Direct.of("step 1", context -> {
    }),
    Direct.of("step 2", context -> {
    }),
    Direct.of("step 3", context -> {
    })
));
```

## Key Behavior

- Executes children sequentially from first to last
- **All children execute regardless of failures** — even if a child fails, subsequent children still run
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip
- Parent-child callbacks fire for each child

## Example: Ordered Test Steps

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("DatabaseTest",
        Direct.of("create database", context -> createDatabase()),
        Direct.of("insert data", context -> insertData()),
        Direct.of("query data", context -> queryData()),
        Direct.of("drop database", context -> dropDatabase()));
}
```

## When to Use

| Situation | Use Sequential |
|-----------|---------------|
| Independent tests | ✅ Yes |
| Want all results regardless of failures | ✅ Yes |
| Ordered execution required | ✅ Yes |

## See Also

- [StrictSequential](./strict-sequential) - Stops on first failure
- [RandomSequential](./random-sequential) - Randomized order execution
- [Action Composition](../usage/action-composition) - Nested and flat composition patterns
- [Error Handling](../usage/error-handling) - How failures propagate
