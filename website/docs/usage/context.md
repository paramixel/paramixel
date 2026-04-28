---
id: context
title: Context
description: Access runtime state during action execution
---

# Context

The `Context` interface provides access to runtime state during action execution.

## Context Interface

```java
import org.paramixel.core.Context;

public interface Context {
    Optional<Context> parent();
    Action action();
    <T> Context setAttachment(T attachment);
    <T> Optional<T> attachment(Class<T> type);
    Optional<Object> removeAttachment();
    Context createChild(Action child);
    Result execute(Action child);
    CompletableFuture<Result> executeAsync(Action child);
    void beforeAction(Context context, Action action);
    void afterAction(Context context, Action action, Result result);
}
```

## Accessing Context

The `Context` is passed to `Executable` callbacks:

```java
Direct.of("my test", context -> {
        // Access context here
        context.setAttachment(new TestData("value"));
        TestData data = context.attachment(TestData.class).orElseThrow();
    }));
```

## Context Hierarchy

Each `Context` has an optional parent, forming a hierarchy that mirrors the action tree:

```
Context (root)
├── Context (child 1)
│   └── Context (grandchild 1)
└── Context (child 2)
```

### Navigating the Hierarchy

```java
Context current = ...;
Optional<Context> parent = current.parent();
Optional<Context> grandparent = parent.flatMap(Context::parent);
```

### Example: Accessing Ancestor Contexts

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Sequential.of("outer",
            Sequential.of("inner",
                Direct.of("leaf", context -> {
                    // Navigate up the hierarchy
                    Optional<Context> inner = context.parent();
                    Optional<Context> outer = inner.flatMap(Context::parent);

                    assertThat(inner).isPresent();
                    assertThat(outer).isPresent();

                    Action leafAction = context.action();
                    Action innerAction = inner.orElseThrow().action();
                    Action outerAction = outer.orElseThrow().action();

                    assertThat(leafAction.name()).isEqualTo("leaf");
                    assertThat(innerAction.name()).isEqualTo("inner");
                    assertThat(outerAction.name()).isEqualTo("outer");
                })))));
}
```

## Action

```java
Action action = context.action();
String name = action.name();
String id = action.id();
Optional<Action> parent = action.parent();
```

The action associated with the current context. Use this to access action metadata during execution:

```java
Direct.of("test argument A", context -> {
    // Get the action name (includes the argument name)
    String actionName = context.action().name();
    assertThat(actionName).isEqualTo("test argument A");

    // Access parent action
    Optional<Action> parent = context.action().parent();
    if (parent.isPresent()) {
        System.out.println("Parent: " + parent.get().name());
    }
}));
```

## Attachments

Contexts support attaching a single typed object for the duration of the action's execution:

```java
context.setAttachment(new Attachment("hello"));
Attachment att = context.attachment(Attachment.class).orElseThrow();
```

### Using In-Class Records

The recommended pattern is to define an in-class `record` for each test's attachment needs:

```java
record Attachment(String message) {}

@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Direct.of("setup", context -> {
            context.setAttachment(new Attachment("hello"));
        }),
        Direct.of("verify", context -> {
            Attachment att = context.attachment(Attachment.class).orElseThrow();
            assertThat(att.message()).isEqualTo("hello");
        }));
}
```

### Removing Attachments

```java
Optional<Object> removed = context.removeAttachment();
```

Clears the attachment and returns the removed value. Useful in teardown:

```java
Lifecycle.of("MyTest",
    context -> {
        context.setAttachment(new Resource(...));
    },
    Direct.of("test", context -> {
        Resource res = context.attachment(Resource.class).orElseThrow();
    }),
    context -> {
        context.removeAttachment();
    });
```

### Attachment Isolation

Each context has its own independent attachment. Child contexts do **not** inherit parent attachments. To share data across the context hierarchy, navigate the parent chain:

```java
// In a child action, access parent's attachment
Attachment att = context.parent()
    .orElseThrow()
    .attachment(Attachment.class)
    .orElseThrow();
```

## Executing Child Actions

```java
Action nested = Direct.of("child",
    childContext -> {
    });

Result result = context.execute(nested);
assertThat(result.status()).isEqualTo(Result.Status.PASS);
```

### Synchronous Execution

```java
Result result = context.execute(childAction);
```

Executes the child synchronously and returns the result.

### Asynchronous Execution

```java
CompletableFuture<Result> future = context.executeAsync(childAction);
Result result = future.join();
```

Executes the child asynchronously; useful from parallel actions.

### Creating Child Contexts

```java
Context childContext = context.createChild(childAction);
```

Creates a child context without executing. Useful for implementing custom actions.

### Listener Callbacks

```java
context.beforeAction(context, action);
context.afterAction(context, action, result);
```

Manually trigger listener notifications. Composite actions call these before/after executing children.

## Listener Notifications

The `Context` interface provides `beforeAction()` and `afterAction()` methods for triggering listener callbacks:

```java
context.beforeAction(context, childAction);
Result result = childAction.execute(childContext);
context.afterAction(context, childAction, result);
```

The built-in composite actions (`Sequential`, `Parallel`, `Lifecycle`, `StrictSequential`) automatically call these methods before and after executing children. Use them when implementing custom actions to ensure listeners receive proper notifications.

See [Architecture](../architecture) for details on the listener architecture.

## Context and Lifecycle

In a `Lifecycle` action, the same context is used for setup, body, and teardown:

```java
record Attachment(Resource resource) {}

Lifecycle.of("MyTest",
    context -> {
        // Setup: attach resource
        Resource res = new Resource();
        context.setAttachment(new Attachment(res));
    },
    Direct.of("test", context -> {
        // Body: access attached resource
        Attachment att = context.attachment(Attachment.class).orElseThrow();
        att.resource().doSomething();
    }),
    context -> {
        // Teardown: clean up
        context.removeAttachment();
    });
```

## See Also

- [Action Composition](action-composition) - Building action trees
- [Lifecycle](../actions/lifecycle) - Setup and teardown with Context access
- [Integration Testing](integration-testing) - Testcontainers patterns with attachments
- [Architecture](../architecture) - Context tree structure
