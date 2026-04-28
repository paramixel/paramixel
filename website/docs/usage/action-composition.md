---
id: action-composition
title: Action Composition
description: Build complex action trees by composing simple actions
---

# Action Composition

Paramixel's core abstraction is the `Action` — a named unit of executable work. Actions form a tree structure where parent actions coordinate the execution of their child actions.

## Action

The `Action` interface represents a named unit of work:

```java
Action action = Direct.of("my action",
    context -> {
    });
```

### Action Properties

- **`id()`** — Unique identifier (UUID)
- **`name()`** — Display name for reporting
- **`parent()`** — Optional parent in the action tree
- **`children()`** — List of child actions
- **`execute(Context)`** — Execution hook

## Composition Patterns

### Flat Sequential

Simple ordered list of tests:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Direct.of("test 1", context -> {
        }),
        Direct.of("test 2", context -> {
        }),
        Direct.of("test 3", context -> {
        })
    );
}
```

### Nested Sequential

Group related tests:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Sequential.of("database tests",
            Direct.of("create", context -> {
            }),
            Direct.of("read", context -> {
            }),
            Direct.of("update", context -> {
            }),
            Direct.of("delete", context -> {
            })
        ),
        Sequential.of("cache tests",
            Direct.of("put", context -> {
            }),
            Direct.of("get", context -> {
            })
        )
    );
}
```

### Parallel Matrix with Sequential Methods

Run arguments in parallel, methods sequentially:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    String[] arguments = {"A", "B", "C"};
    List<Action> argumentActions = new ArrayList<>();
    for (String arg : arguments) {
        argumentActions.add(
            Sequential.of(arg + " methods",
                Direct.of("test 1", context -> {
                }),
                Direct.of("test 2", context -> {
                })
            )
        );
    }
    return Parallel.of("MyTest", 2, argumentActions);
}
```

### Multi-Level Parallelism

Parallel at different levels of the tree:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    List<Action> groupActions = new ArrayList<>();
    for (int group = 1; group <= 3; group++) {
        List<Action> childActions = new ArrayList<>();
        for (int child = 1; child <= 4; child++) {
            childActions.add(
                Direct.of("child " + child, context -> {
                })
            );
        }
        groupActions.add(
            Parallel.of("children", 2, childActions));
    }
    return Parallel.of("MyTest", 2, groupActions);
}
```

### Nested Lifecycles

Lifecycles can be nested for hierarchical resource management:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("integration test",
        context -> setupGlobalResources(),
        Sequential.of("feature tests",
            Lifecycle.of("feature 1",
                context -> setupFeature1Resources(),
                Direct.of("test feature 1", context -> {
                }),
                context -> cleanupFeature1Resources()
            ),
            Lifecycle.of("feature 2",
                context -> setupFeature2Resources(),
                Direct.of("test feature 2", context -> {
                }),
                context -> cleanupFeature2Resources()
            )
        ),
        context -> cleanupGlobalResources()
    );
}
```

Teardown runs from innermost to outermost: feature cleanup, then global cleanup.

### Conditional Composition

Build action trees conditionally:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    List<Action> children = new ArrayList<>();
    children.add(Direct.of("always run", context -> {
    }));

    if (runExtendedTests()) {
        children.add(Direct.of("extended test", context -> {
        }));
    }

    return Sequential.of("MyTest", children);
}
```

### Dynamic Tree Building

Create actions dynamically:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    List<Action> versionTests = new ArrayList<>();
    for (String version : supportedVersions()) {
        versionTests.add(
            Sequential.of(version + " tests",
                Direct.of("test 1", context -> testVersion(version, 1)),
                Direct.of("test 2", context -> testVersion(version, 2))
            )
        );
    }
    return Parallel.of("version tests", 4, versionTests);
}
```

## Action Naming

Use descriptive names for debugging and reporting:

```java
Direct.of("test user creation with valid data", context -> {
});
```

The name appears in:
- Console output (from the `Listener`)
- Maven plugin summary table
- `Result.action().name()`

## Custom Actions

Paramixel's architecture is extensible: actions own their execution logic. You can create custom actions by extending `AbstractAction` and implementing `doExecute(Context, Instant)`.

### Basic Custom Action

```java
import org.paramixel.core.action.AbstractAction;

public class RetryAction extends AbstractAction {

    private final int maxAttempts;
    private final Action delegate;

    public RetryAction(String name, int maxAttempts, Action delegate) {
        super(name);
        this.maxAttempts = maxAttempts;
        this.delegate = delegate;
        adopt(delegate);
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        Throwable lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Result result = context.execute(delegate);

            if (result.status() == Result.Status.PASS) {
                return Result.pass(this, durationSince(start));
            }

            lastFailure = result.failure().orElse(null);

            if (attempt < maxAttempts) {
                Thread.sleep(1000);
            }
        }

        return Result.fail(this, durationSince(start), lastFailure);
    }

    @Override
    public List<Action> children() {
        return List.of(delegate);
    }
}
```

Usage:

```java
Action test = Direct.of("flaky test", context -> {
});

Action retryingTest = new RetryAction("retrying flaky test", 3, test);

Sequential.of("MyTest",
    Direct.of("stable test", context -> {
    }),
    retryingTest
);
```

### Parallel Batch Action

```java
import org.paramixel.core.action.AbstractAction;

public class ParallelBatchAction extends AbstractAction {

    private final int batchSize;
    private final List<Action> actions;

    public ParallelBatchAction(String name, int batchSize, List<Action> actions) {
        super(name);
        this.batchSize = batchSize;
        this.actions = List.copyOf(actions);
        this.actions.forEach(this::adopt);
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        List<Result> allResults = new ArrayList<>();

        for (int i = 0; i < actions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, actions.size());
            List<Action> batch = actions.subList(i, end);

            List<Result> batchResults = batch.stream()
                .map(action -> context.executeAsync(action))
                .map(CompletableFuture::join)
                .toList();

            allResults.addAll(batchResults);
        }

        return Result.of(
            this,
            computeStatus(allResults),
            durationSince(start),
            findFailure(allResults),
            allResults
        );
    }

    @Override
    public List<Action> children() {
        return actions;
    }
}
```

### Key Points for Custom Actions

1. **`doExecute(Context, Instant)`** is your extension point. Return a `Result` using `Result.pass()`, `Result.fail()`, `Result.skip()`, or `Result.of()`.

2. **`context.execute(Action)`** for synchronous child execution — use from sequential actions.

3. **`context.executeAsync(Action)`** for asynchronous child execution — use from parallel actions.

4. **Call `adopt(child)`** for each child action to establish parent links.

5. **Override `children()`** to expose child actions for reporting.

6. **Use `durationSince(start)`** to compute timing — the `start` instant is provided by `doExecute()`.

7. **Use `computeStatus(results)` and `findFailure(results)`** helpers (from `AbstractAction`) for composite actions.

This design requires no changes to the executor. Custom actions integrate seamlessly by following the same pattern as built-in composite actions.

## See Also

- [Direct](../actions/direct) - Leaf action that executes a callback
- [Noop](../actions/noop) - Leaf action that does nothing
- [Sequential](../actions/sequential) - Execute actions sequentially
- [StrictSequential](../actions/strict-sequential) - Sequential with fail-fast
- [RandomSequential](../actions/random-sequential) - Randomized order execution
- [StrictRandomSequential](../actions/strict-random-sequential) - Randomized with fail-fast
- [Parallel](../actions/parallel) - Execute actions concurrently
- [Lifecycle](../actions/lifecycle) - Setup and teardown with guaranteed cleanup
- [Error Handling](./error-handling) - How failures and skips propagate
