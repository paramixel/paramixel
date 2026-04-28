---
id: strict-sequential
title: StrictSequential
description: Execute child actions sequentially, stopping on first failure
---

# StrictSequential

`StrictSequential` runs child actions sequentially but stops on first failure.

## Creating StrictSequential Actions

```java
import org.paramixel.core.action.StrictSequential;

Action action = StrictSequential.of("my strict sequential", List.of(
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
- **Stops on first failure** — if a child fails, subsequent children are not executed
- Remaining children are reported as `SKIPPED` with proper listener callbacks (before/after still fire)
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip

## Example: Dependent Test Steps

Use StrictSequential when later steps depend on earlier ones succeeding:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return StrictSequential.of("UserCreationTest",
        Direct.of("create user", context -> createUser()),
        Direct.of("verify user exists", context -> verifyUserExists()),
        Direct.of("delete user", context -> deleteUser()));
}
```

If "create user" fails, "verify user exists" and "delete user" are skipped because there's no user to verify or delete.

## When to Use StrictSequential vs Sequential

| Situation | Use Sequential | Use StrictSequential |
|-----------|---------------|---------------------|
| Independent tests | ✅ Yes | ❌ No |
| Dependent steps (setup, verify, cleanup) | ❌ No | ✅ Yes |
| Want all results regardless | ✅ Yes | ❌ No |
| Stop on first failure to save time | ❌ No | ✅ Yes |

## See Also

- [Sequential](./sequential) - Executes all children regardless of failures
- [StrictRandomSequential](./strict-random-sequential) - Randomized order with fail-fast
- [Error Handling](../usage/error-handling) - How failures and skips propagate
