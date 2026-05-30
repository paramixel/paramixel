---
title: Best Practices
description: Recommended patterns and practices for Paramixel projects.
---

# Best Practices

## Action factory method pattern

Create each action using a `private static` method. This makes the test structure visible in the IDE outline and enables direct click-to-navigate:

```java
@Paramixel.Factory
public static Action<?> factory() {
    Action<?> first = first();
    Action<?> second = second();

    return Lifecycle.of("MyTest")
            .child(first)
            .child(second)
            .resolve();
}

private static Action<?> first() {
    return Step.of("first", ctx -> { /* test logic */ });
}

private static Action<?> second() {
    return Step.of("second", ctx -> { /* test logic */ });
}
```

Always assign actions to local variables before passing to specs. Avoid inline method calls inside spec chains.

## Choose the right action type

| Use case | Action |
| --- | --- |
| Single step | `Step` |
| Setup/body/teardown with managed instance | `Instance` |
| Setup/body/teardown without instance | `Lifecycle` or `Static` |
| Ordered dependent tests | `Lifecycle` (dependent) |
| Ordered independent tests | `Sequential` (independent) |
| Concurrent tests | `Parallel` |
| Static setup/teardown (no instance) | `Static` |

## Use Instance for managed state

When tests need a shared mutable object with automatic cleanup, use `Instance`:

```java
Instance.of("user-service", UserService::new)
        .child("create-user", UserService::createUser)
        .child("get-user", UserService::getUser)
        .resolve();
```

`Instance` auto-generates `Instantiate` and `Destroy` steps for setup and teardown. `AutoCloseable` instances are automatically closed in the generated `Destroy` step.

## Use AnnotationResolver for annotated methods

For test classes with many methods, `AnnotationResolver` resolves `@Paramixel.Id` methods into `Step` actions:

```java
var resolver = AnnotationResolver.create(MyTest.class);

Instance.of(MyTest.class)
        .child(resolver.byId("login"))
        .child(resolver.byId("verify"))
        .child(resolver.byId("logout"))
        .resolve();
```

## Use the Spec + loop pattern for parameterized tests

`Lifecycle`, `Sequential`, `Parallel`, `Instance`, and `Static` also provide `each(Iterable, Function)` as a convenience method that produces the same tree:

### For-loop

```java
var spec = Parallel.of("suite").parallelism(4);
for (TestCase tc : testCases()) {
    spec.child(argument(tc));
}
return spec.resolve();
```

### Each convenience method

```java
return Parallel.of("suite")
        .parallelism(4)
        .each(testCases(), tc -> argument(tc))
        .resolve();
```

## Keep actions immutable

Actions are reusable across runs. Do not store mutable state in action fields. Use `Instance` for per-execution state, or `Context#instance(Class)`.

## Respect the Action contract

When implementing custom actions:

1. Set the descriptor status to `RUNNING` and then to a terminal `Status`.
2. Fire listener callbacks before and after the action boundary using `listener.onBeforeExecution(descriptor)` and `listener.onAfterExecution(descriptor)`.
3. Respect `Mode` — handle `SKIP` and `ABORT` modes.
4. For most use cases, prefer composing built-in actions rather than implementing `Action` directly.

## Use CleanUp for resource teardown

`CleanUp` captures teardown failures and can aggregate multiple cleanup failures with suppressed exceptions:

```java
import org.paramixel.api.support.CleanUp;

CleanUp.runAndThrow(
        CleanUp.of(() -> closeResourceA()),
        CleanUp.of(resourceB));
```

## Use Retry for transient failures

`Retry` retries an operation with configurable backoff:

```java
import org.paramixel.api.support.Retry;
import org.paramixel.api.support.Retry.Policy;

Retry.of(Policy.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
        .run(() -> {
            // operation that may fail transiently
        });
```

See [Retry and CleanUp](../api/retry-and-cleanup) for the full API.

## Configure reports for CI

Always configure a report file in CI for archival:

```bash
./mvnw test -Dparamixel.report.file=target/paramixel/report.json
./gradlew paramixelTest -Dparamixel.report.file=build/paramixel/report.json
```

## Set parallelism appropriately

On CI runners with limited CPU, set an explicit parallelism to prevent oversubscription:

```bash
./mvnw test -Dparamixel.parallelism=4
```

## Next steps

- [Actions](../core-concepts/actions)
- [Elements](../core-concepts/elements)
- [Custom Actions](./custom-actions)
