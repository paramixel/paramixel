---
id: strict-random-sequential
title: StrictRandomSequential
description: Execute child actions in random order, stopping on first failure
---

# StrictRandomSequential

`StrictRandomSequential` runs child actions in random order but stops on first failure.

## Creating StrictRandomSequential Actions

```java
import org.paramixel.core.action.StrictRandomSequential;

Action action = StrictRandomSequential.of("my strict random sequential", List.of(
    Direct.of("step 1", context -> {
    }),
    Direct.of("step 2", context -> {
    }),
    Direct.of("step 3", context -> {
    })
));
```

## Seeded Randomization

Like RandomSequential, StrictRandomSequential supports reproducible ordering:

```java
Action action = StrictRandomSequential.of("my strict random sequential", 42L, List.of(
    Direct.of("step 1", context -> {
    }),
    Direct.of("step 2", context -> {
    }),
    Direct.of("step 3", context -> {
    })
));
```

## Key Behavior

- Executes children in random order
- **Stops on first failure** — if a child fails, subsequent children are not executed
- Remaining children are reported as `SKIPPED` with proper listener callbacks (before/after still fire)
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip

## Example: Randomized Dependent Tests

Use StrictRandomSequential for tests with dependencies when order shouldn't matter:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return StrictRandomSequential.of("UserWorkflowTest", 42L,
        Direct.of("create user", context -> createUser()),
        Direct.of("verify user", context -> verifyUser()),
        Direct.of("delete user", context -> deleteUser()));
}
```

## When to Use Each Sequential Variant

| Situation | Sequential | StrictSequential | RandomSequential | StrictRandomSequential |
|-----------|-----------|------------------|------------------|------------------------|
| Ordered execution | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| Randomized execution | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| All children execute | ✅ Yes | ❌ No | ✅ Yes | ❌ No |
| Stop on first failure | ❌ No | ✅ Yes | ❌ No | ✅ Yes |
| Independent tests | ✅ | ❌ | ✅ (recommended) | ❌ |
| Dependent steps | ❌ | ✅ | ❌ | ✅ (if order shouldn't matter) |
| Detect order dependencies | ❌ | ❌ | ✅ | ❌ |

## See Also

- [RandomSequential](./random-sequential) - Executes all children regardless of failures
- [StrictSequential](./strict-sequential) - Ordered fail-fast execution
- [Error Handling](../usage/error-handling) - How failures and skips propagate
