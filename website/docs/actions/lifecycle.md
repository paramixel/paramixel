---
id: lifecycle
title: Lifecycle
description: Setup and teardown with guaranteed cleanup
---

# Lifecycle

`Lifecycle` provides setup and teardown phases for actions, ensuring teardown always runs even on failure.

## Creating Lifecycle Actions

### Body Only

```java
import org.paramixel.core.action.Lifecycle;

Lifecycle.of("my action", bodyAction)
```

### Setup + Body

```java
Lifecycle.of("my action", setupExecutable, bodyAction)
```

### Body + Teardown

```java
Lifecycle.of("my action", bodyAction, teardownExecutable)
```

### Full Lifecycle

```java
Lifecycle.of("my action", setupExecutable, bodyAction, teardownExecutable)
```

`Executable` is the functional interface for setup and teardown:

```java
@FunctionalInterface
public interface Executable {
    void execute(Context context) throws Throwable;
}
```

## Setup Behavior

The setup phase runs before the body:

- If setup **succeeds** → body executes
- If setup throws `SkipException` → body is **skipped**, teardown runs
- If setup throws any other exception → body is **skipped**, teardown runs

### Example: Setup That May Skip

```java
Lifecycle.of("feature test",
    context -> {
        if (!featureEnabled()) {
            throw new SkipException("Feature disabled");
        }
    },
    Direct.of("test feature", context -> {
    })
)
```

## Teardown Behavior

Teardown runs after the body completes, **always**:

- Body throws an exception → teardown runs
- Body is skipped → teardown runs
- Setup fails → body skipped, but teardown runs
- Teardown itself fails → failure is reported

### Teardown Guarantees

- Teardown runs even if setup or body throws
- Multiple levels of nesting: inner teardown runs before outer teardown
- Attachment state persists from setup through teardown

### Example: Guaranteed Cleanup

```java
Lifecycle.of("database test",
    context -> createDatabase(),
    Direct.of("run queries", context -> {
        runQuery("SELECT * FROM users");
    }),
    context -> dropDatabase()
)
```

If `runQuery` fails, `dropDatabase` still executes.

## Skipping

### Skip During Body

If body throws `SkipException`, teardown still runs:

```java
Lifecycle.of("test",
    context -> setup(),
    Direct.of("test", context -> {
        if (conditionNotMet()) {
            throw new SkipException("Condition not met");
        }
    }),
    context -> cleanup()
)
```

### Skip During Setup

If setup throws `SkipException`:
- Body is skipped
- Teardown runs

### Skip During Teardown

Teardown itself can be skipped if it throws `SkipException`. This is rare — typically teardown should run regardless.

## Nested Lifecycles

Inner lifecycles' teardown runs before outer lifecycles' teardown:

```java
Lifecycle.of("outer",
    context -> outerSetup(),
    Lifecycle.of("inner",
        context -> innerSetup(),
        Direct.of("test", context -> {
        }),
        context -> innerTeardown()  // Runs first
    ),
    context -> outerTeardown()  // Runs second
)
```

**Execution order:**
1. Outer setup
2. Inner setup
3. Test
4. Inner teardown
5. Outer teardown

## Attachment Integration

Attachment state persists across setup, body, and teardown (they all share the same context):

```java
record Attachment(Database database) {}

Lifecycle.of("database test",
    context -> {
        Database db = createDatabase();
        context.setAttachment(new Attachment(db));
    },
    Direct.of("test", context -> {
        Attachment att = context.attachment(Attachment.class).orElseThrow();
        att.database().query("SELECT * FROM users");
    }),
    context -> {
        Attachment att = context.attachment(Attachment.class).orElseThrow();
        att.database().close();
    }
)
```

## Example: Testcontainers Pattern

```java
record Attachment(PostgreSQLContainer container) {}

@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("PostgreSQL integration test",
        context -> {
            PostgreSQLContainer container = new PostgreSQLContainer("postgres:16");
            container.start();
            context.setAttachment(new Attachment(container));
        },
        Sequential.of("tests",
            Direct.of("test 1", context -> {
                Attachment att = context.attachment(Attachment.class).orElseThrow();
                String jdbcUrl = att.container().getJdbcUrl();
            }),
            Direct.of("test 2", context -> {
                Attachment att = context.attachment(Attachment.class).orElseThrow();
                String jdbcUrl = att.container().getJdbcUrl();
            })
        ),
        context -> {
            Attachment att = context.attachment(Attachment.class).orElseThrow();
            att.container().stop();
        }
    );
}
```

## When to Use Lifecycle

| Situation | Use Lifecycle |
|-----------|---------------|
| Resource allocation/cleanup (databases, containers, files) | ✅ Yes |
| Setup required before tests | ✅ Yes |
| Cleanup must run regardless of test outcome | ✅ Yes |
| Shared setup across multiple tests | ✅ Yes |

## See Also

- [Action Composition](../usage/action-composition) - Nesting Lifecycles with other actions
- [Context](../usage/context) - Attachment pattern for resource sharing
- [Parallel](./parallel) - Running lifecycles concurrently
- [Error Handling](../usage/error-handling) - How failures propagate
