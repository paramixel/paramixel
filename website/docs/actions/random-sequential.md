---
id: random-sequential
title: RandomSequential
description: Execute child actions in random order
---

# RandomSequential

`RandomSequential` runs child actions in random order.

## Creating RandomSequential Actions

```java
import org.paramixel.core.action.RandomSequential;

Action action = RandomSequential.of("my random sequential", List.of(
    Direct.of("step 1", context -> {
    }),
    Direct.of("step 2", context -> {
    }),
    Direct.of("step 3", context -> {
    })
));
```

## Seeded Randomization

For reproducible ordering (useful for debugging), provide a seed:

```java
Action action = RandomSequential.of("my random sequential", 42L, List.of(
    Direct.of("step 1", context -> {
    }),
    Direct.of("step 2", context -> {
    }),
    Direct.of("step 3", context -> {
    })
));
```

The same seed always produces the same execution order across runs.

## Key Behavior

- Executes children in random order
- **All children execute regardless of failures** — even if a child fails, subsequent children still run
- Status is `FAIL` if any child fails, `PASS` if all pass, `SKIP` if all skip

## Example: Order-Independent Tests

Use RandomSequential to verify tests don't depend on execution order:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return RandomSequential.of("CacheTest", 42L,
        Direct.of("test put", context -> putIntoCache()),
        Direct.of("test get", context -> getFromCache()),
        Direct.of("test invalidate", context -> invalidateCache()));
}
```

If tests are order-dependent, RandomSequential will help identify the issue.

## When to Use

| Situation | Use RandomSequential |
|-----------|---------------------|
| Detect order dependencies | ✅ Yes |
| Independent tests | ✅ Recommended |
| Stress test execution order | ✅ Yes |
| Ordered execution required | ❌ No |

## See Also

- [StrictRandomSequential](./strict-random-sequential) - Randomized with fail-fast
- [Sequential](./sequential) - Ordered execution
- [Error Handling](../usage/error-handling) - How failures propagate
