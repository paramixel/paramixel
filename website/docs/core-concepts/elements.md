---
title: Elements
description: Built-in action types — Step, Lifecycle, Sequential, Parallel, Instance, Static, Delay, Repeat, Timeout, AssertTrue, AssertFalse.
---

# Elements

Paramixel provides eleven built-in action types. Each has a specific composition model and use case.

## Spec method semantics

Paramixel spec methods fall into two categories:

| Semantics | Methods | Behavior |
| --- | --- | --- |
| **Overwrite** | `before()`, `after()` on Lifecycle, Static; `child()` on Repeat, Timeout | Calling the method again replaces the previous value. The last call wins. |
| **Append** | `child()` on Sequential, Parallel, Lifecycle, Static, Instance | Each call adds another child to the list. |

### Kind-parameter overloads

All shorthand `before()`, `child()`, and `after()` methods that accept a name string also accept a kind string as a second parameter before the callback. Use the kind parameter to override the default kind in console output and reports:

```java
Lifecycle.of("suite")
        .before("setup", "DatabaseSetup", ctx -> { /* ... */ })
        .child("test", "DbQuery", ctx -> { /* ... */ })
        .after("teardown", "DatabaseTeardown", ctx -> { /* ... */ })
        .resolve();
```

This follows the same pattern as `Step.of(name, kind, consumer)`.

If you need multiple setup or teardown actions, compose them in a composite action:

```java
Lifecycle.of("suite")
        .before(Sequential.of("setup")
                .child(Step.of("db-init", ctx -> { /* ... */ }))
                .child(Step.of("cache-warm", ctx -> { /* ... */ }))
                .resolve())
        .child(Step.of("test", ctx -> { /* ... */ }))
        .resolve();
```

## Step

`Step` is the simplest runnable action. It executes a single `ThrowingConsumer` callback and maps the outcome to a status.

| Outcome | Status |
| --- | --- |
| Normal completion | `PASSED` |
| `SkipException` | `SKIPPED` |
| `AbortedException` | `ABORTED` |
| `FailException` | `FAILED` |
| Any other throwable | `FAILED` (throwable attached) |

### Create a step

```java
import org.paramixel.api.action.Step;

Action<?> step = Step.of("login", instance -> {
    // test logic here
});
```

When the execution context has an instance (from an `Instance` action), the consumer receives the instance object. When no instance is present, the consumer receives the `ExecutionContext` itself (context mode). See [ExecutionContext](../api/execution-context) for the full context API.

A step with a custom kind can be created with `Step.of(name, kind, consumer)`. The kind overrides the default `"Step"` kind in console output and reports.

`Step.kind(String kind)` returns a new step with the same name and consumer but a different kind. The original step is not modified.

### Skip, abort, and fail

```java
import org.paramixel.api.exception.SkipException;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;

Step.of("conditional", instance -> {
    if (!environmentAvailable()) {
        throw new SkipException("environment not available");
    }
});

Step.of("requires-db", instance -> {
    if (!databaseReady()) {
        throw new AbortedException("database not ready");
    }
});

Step.of("validation", instance -> {
    if (!isValid()) {
        throw new FailException("validation failed");
    }
});
```

### With Instance propagation

When a `Step` runs inside an `Instance` action, it receives the managed instance:

```java
Instance.of("user-test", UserService::new)
        .child("create-user", UserService::createUser)
        .resolve();
```

## Lifecycle

`Lifecycle` runs optional setup, ordered body children, and optional teardown as a single composition.

### Structure

```
Lifecycle "suite"
├── before (optional)
├── body child 1
├── body child 2
└── after (optional, always runs)
```

- **`before`** runs first. If it fails, skips, or aborts, body children receive propagated mode. Calling `before()` again overwrites the previous value.
- **Body children** run in declaration order. Dependent by default (fail-fast).
- **`after`** always runs as `RUN` mode regardless of earlier outcomes — suitable for cleanup. Calling `after()` again overwrites the previous value.

### Dependent body children (default)

After the first failed, skipped, or aborted body child, remaining body children receive `SKIP` mode (or `ABORT` if the earlier child aborted).

```java
Lifecycle.of("dependent-suite")
        .before(Step.of("setup", ctx -> { /* setup */ }))
        .child(Step.of("test-1", ctx -> { /* test */ }))
        .child(Step.of("test-2", ctx -> { /* test */ }))
        .after(Step.of("teardown", ctx -> { /* cleanup */ }))
        .resolve();
```

### Independent body children

All body children run regardless of earlier outcomes:

```java
Lifecycle.of("independent-suite")
        .independent()
        .child(Step.of("test-1", ctx -> { /* test */ }))
        .child(Step.of("test-2", ctx -> { /* test */ }))
        .resolve();
```

### Status aggregation

`Lifecycle` aggregates child statuses: `FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`.

The built `Lifecycle` action provides `isDependent()` and `isIndependent()` to query the body child dependency mode.

## Sequential

`Sequential` runs children one at a time in declaration order. Similar to `Lifecycle` but without `before`/`after` phases. Each `child()` call appends to the list of body children.

The built `Sequential` action provides `isDependent()` and `isIndependent()` to query the child dependency mode.

### Dependent (default)

```java
Sequential.of("ordered-tests")
        .child(Step.of("first", ctx -> {}))
        .child(Step.of("second", ctx -> {}))
        .resolve();
```

### Independent

```java
Sequential.of("run-all-tests")
        .independent()
        .child(Step.of("first", ctx -> {}))
        .child(Step.of("second", ctx -> {}))
        .resolve();
```

### With consumer shorthand

```java
Sequential.of("suite")
        .child("step-1", instance -> { /* logic */ })
        .child("step-2", instance -> { /* logic */ })
        .resolve();
```

## Parallel

`Parallel` runs children concurrently, bounded by a configurable parallelism limit. All children are always submitted regardless of individual outcomes. Each `child()` call appends to the list of body children.

The built `Parallel` action provides `parallelism()` to query the configured parallelism limit.

### Basic

```java
Parallel.of("concurrent-tests")
        .parallelism(4)
        .child(Step.of("test-a", ctx -> {}))
        .child(Step.of("test-b", ctx -> {}))
        .child(Step.of("test-c", ctx -> {}))
        .resolve();
```

### Unlimited parallelism (default)

```java
Parallel.of("all-at-once")
        .child(Step.of("test-a", ctx -> {}))
        .child(Step.of("test-b", ctx -> {}))
        .resolve();
```

### Status aggregation

`Parallel` computes status after all children complete: `FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`.

### With consumer shorthand

```java
Parallel.of("parallel-suite")
        .parallelism(2)
        .child("test-a", instance -> { /* logic */ })
        .child("test-b", instance -> { /* logic */ })
        .resolve();
```

## Instance

`Instance` manages the lifecycle of a factory-created object. It creates the instance, makes it available to children via `ExecutionContext#instance`, and destroys it after execution (auto-closing if it implements `AutoCloseable`). The `Instantiate` and `Destroy` steps are auto-generated.

### With a Supplier

```java
Instance.of("user-service-test", UserService::new)
        .child("create-user", UserService::createUser)
        .child("get-user", UserService::getUser)
        .resolve();
```

### With a Class

```java
Instance.of(UserService.class)
        .child("create-user", UserService::createUser)
        .resolve();
```

This uses the public no-argument constructor. The display name defaults to the class's simple name.

An instance with a custom name and default constructor can be created with `Instance.of(name, type)`, where `type` must have a public no-argument constructor.

### Internal structure

`Instance` generates an internal action tree:

```
Instance "user-service-test"
├── before (Instantiate - auto)
├── body children (user-configured)
└── after (Destroy - auto)
```

### Child method ordering

:::caution

`Instance.Spec` provides two `child()` overloads:

- `child(String, ThrowingConsumer)` — consumer shorthand, resolved first
- `child(String, String, ThrowingConsumer)` and `child(Spec<?>)` — kind-parameter and spec-based, appended after consumer shorthand

When mixing both overloads, all consumer-shorthand children appear first regardless of call order. Prefer using a single overload type per spec to avoid non-obvious ordering.

:::

### Accessing the instance

Inside child steps, the instance is passed as the callback parameter:

```java
Instance.of("my-test", MyService::new)
        .child("test", service -> {
            service.doSomething();
        })
        .resolve();
```

Programmatically, a context-mode `Step` can use `ExecutionContext#instance(Class)`:

```java
Step.of("custom", context -> {
    var service = context.instance(MyService.class);
    service.ifPresent(MyService::doSomething);
});
```

### Dependent vs independent body children

Like `Lifecycle`, `Instance` defaults to dependent body children:

The built `Instance` action provides `isDependent()` and `isIndependent()` to query the body child dependency mode.

```java
Instance.of("test", MyService::new)
        .independent()  // all body children run regardless of outcomes
        .child("test-a", MyService::testA)
        .child("test-b", MyService::testB)
        .resolve();
```

## Static

`Static` is an instance-free lifecycle with before/body/after phases. Use it for static setup and teardown that doesn't require a managed instance. Calling `before()` or `after()` again overwrites the previous value.

```java
Static.of("static-suite")
        .before("setup", () -> { /* static setup */ })
        .child(Step.of("test-1", ctx -> {}))
        .child(Step.of("test-2", ctx -> {}))
        .after("teardown", () -> { /* static teardown */ })
        .resolve();
```

`Static` accepts `ThrowingRunnable` for `before()`, `after()`, and `child()` shorthands (since there is no instance to receive), and `Action` or `Spec` for body children.

The built `Static` action provides `isDependent()` and `isIndependent()` to query the body child dependency mode.

### Dependent vs independent

```java
Static.of("independent-static")
        .independent()
        .child(Step.of("a", ctx -> {}))
        .child(Step.of("b", ctx -> {}))
        .resolve();
```

## Delay

`Delay` is a leaf action that pauses execution for a fixed or random duration. Use it to introduce deliberate delays — for example, waiting for an asynchronous process to settle or simulating user think time.

### Fixed duration

```java
import org.paramixel.api.action.Delay;

// Milliseconds
Delay.of("wait-for-index", 500);

// java.time.Duration
Delay.of("wait-for-index", Duration.ofMillis(500));
```

### Random duration

`Delay.random()` draws a fresh duration from `ThreadLocalRandom` on each execution, bounded inclusively by `minimumMilliseconds` and `maximumMilliseconds`:

```java
Delay.random("jitter", 100, 300);
```

### Interruption

If the delaying thread is interrupted, `Delay` restores the interrupt flag and fails with `FAILED` status.

### Zero duration

`Delay.of("no-op-pause", 0)` passes immediately without delaying.

## AssertTrue

`AssertTrue` is a leaf action that asserts a boolean condition. It passes when the condition is `true` and fails when `false`.

### With a boolean condition

```java
AssertTrue.of("check-enabled", featureEnabled);

AssertTrue.of("check-enabled", featureEnabled, "Feature must be enabled");
```

### With a BooleanSupplier

```java
AssertTrue.of("check-alive", () -> service.isAlive());

AssertTrue.of("check-alive", () -> service.isAlive(), "Service must be alive");
```

## AssertFalse

`AssertFalse` is a leaf action with inverted logic. It passes when the condition is `false` and fails when `true`.

### With a boolean condition

```java
AssertFalse.of("check-disabled", featureDisabled);

AssertFalse.of("check-disabled", featureDisabled, "Feature must be disabled");
```

### With a BooleanSupplier

```java
AssertFalse.of("check-stopped", () -> service.isStopped());

AssertFalse.of("check-stopped", () -> service.isStopped(), "Service must be stopped");
```

See [Assertions](./assertions) for broader assertion patterns with external libraries.

## Repeat

`Repeat` executes a single child action a configurable number of times. Each repetition is a distinct descriptor occurrence with independent execution state. Calling `child()` again overwrites the previous child.

### Dependent repetitions (default)

After the first failed, skipped, or aborted repetition, remaining repetitions receive propagated mode:

```java
Repeat.of("load-test")
        .count(10)
        .child(Step.of("request", ctx -> { /* send request */ }))
        .resolve();
```

### Independent repetitions

All repetitions run regardless of individual outcomes:

```java
Repeat.of("resilience-check")
        .count(5)
        .independent()
        .child(Step.of("attempt", ctx -> { /* try operation */ }))
        .resolve();
```

### Status aggregation

`Repeat` aggregates child statuses: `FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`.

The built `Repeat` action provides `child()` to access the child action, `repeatCount()` for the repetition count, and `isDependent()`/`isIndependent()` for the dependency mode.

### With consumer shorthand

```java
Repeat.of("retry-logic")
        .count(3)
        .child("step", instance -> { /* logic */ })
        .resolve();
```

## Timeout

`Timeout` executes a single child with a wall-clock deadline. If the child does not complete within the configured duration, the action fails. Calling `child()` again overwrites the previous child.

The built `Timeout` action provides `child()` to access the child action and `timeout()` for the configured deadline.

### Basic usage

```java
Timeout.of("slow-api-test")
        .timeout(Duration.ofSeconds(5))
        .child(Step.of("api-call", ctx -> { /* call external API */ }))
        .resolve();
```

### Timeout in milliseconds

```java
Timeout.of("quick-check")
        .timeoutMillis(500)
        .child(Step.of("ping", ctx -> { /* ping service */ }))
        .resolve();
```

### What happens on timeout

1. The action records `FAILED` status with a timeout message
2. The thread executing the child is interrupted
3. If the child responds to interruption, it completes with `FAILED`
4. If the child ignores interruption, it becomes an orphaned daemon thread

### Status propagation

- Child completes within deadline → child's status propagates (PASSED, SKIPPED, ABORTED, or FAILED)
- Child exceeds deadline → action is FAILED

### With Instance propagation

`Timeout` passes through the fixture instance from a parent `Instance` action. Because `Timeout` schedules its child asynchronously, the `InstanceHolder` flows through the scheduler's context chain:

```java
Instance.of("my-test", MyService::new)
        .child(Timeout.of("with-deadline")
                .timeout(Duration.ofSeconds(5))
                .child("api-call", MyService::callApi)
                .resolve())
        .resolve();
```

The `Step` child receives the `MyService` fixture as its consumer parameter, just as it would without the `Timeout` wrapper.

### Cooperative interruption

Java thread interruption is cooperative. A child that checks `Thread.interrupted()` or is in a blocking call (`Thread.sleep`, I/O, etc.) will respond to interruption immediately. A CPU-bound loop that never checks interruption will continue running in the background.

## Choosing the right action type

| Use case | Action type |
| --- | --- |
| Single test step | `Step` |
| Setup/body/teardown with managed instance | `Instance` |
| Setup/body/teardown without instance | `Lifecycle` or `Static` |
| Ordered dependent tests | `Sequential` or `Lifecycle` (dependent) |
| Ordered independent tests | `Sequential` (independent) |
| Concurrent tests | `Parallel` |
| Static setup/teardown | `Static` |
| Deliberate delay | `Delay` |
| Repeated execution of a single child | `Repeat` |
| Wall-clock deadline for a child | `Timeout` |
| Assert a condition is true | `AssertTrue` |
| Assert a condition is false | `AssertFalse` |
