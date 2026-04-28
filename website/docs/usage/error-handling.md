---
id: error-handling
title: Error Handling
description: How success, failure, and skip work
---

# Error Handling

Paramixel actions can succeed, fail, or skip. This document explains how outcomes propagate through the action tree.

## Result Status

Each `Result` has a `Status`:

```java
public enum Status {
    PASS,   // Action completed successfully
    FAIL,   // Action failed
    SKIP    // Action was skipped
}
```

## Success

An action succeeds if it completes without throwing an exception:

```java
Direct.of("passing test", context -> {
        assertThat(2 + 2).isEqualTo(4);
    }));
```

Status: `PASS`

## FailException

Throw `FailException` to explicitly mark an action as failed:

```java
import org.paramixel.core.FailException;

Direct.of("explicit fail", context -> {
        FailException.fail("This test fails");
    }));

Direct.of("fail with message", context -> {
        FailException.fail("Unexpected value", value);
    }));
```

Convenience methods:

```java
FailException.fail();
FailException.fail("message");
FailException.fail("message", cause);
```

Status: `FAIL`

## SkipException

Throw `SkipException` to mark an action as skipped:

```java
import org.paramixel.core.SkipException;

Direct.of("conditional skip", context -> {
        if (featureNotAvailable()) {
            SkipException.skip("Feature not available");
        }
    }));
```

Convenience methods:

```java
SkipException.skip();
SkipException.skip("message");
SkipException.skip("message", cause);
```

Status: `SKIP`

## Unhandled Exceptions

Any unhandled exception (other than `SkipException`) marks an action as failed:

```java
Direct.of("runtime exception", context -> {
        throw new RuntimeException("Something went wrong");
    }));

Direct.of("assertion error", context -> {
        assertThat(value).isEqualTo(expected);
    }));
```

Status: `FAIL`

## Sequential: Failure Propagation

`Sequential` runs all children even if some fail:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Direct.of("test 1", context -> {
            })),
        Direct.of("test 2", context -> {
                throw new AssertionError("This fails");
            })),
        Direct.of("test 3", context -> {
                System.out.println("This still runs");
            })));
}
```

Output:
- test 1: `PASS`
- test 2: `FAIL`
- test 3: `PASS`

Overall status: `FAIL`

All children execute regardless of failures. Use `StrictSequential` for fail-fast behavior that stops on the first failure.

## StrictSequential: Failure Propagation

`StrictSequential` stops execution on the first failure and skips remaining children:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return StrictSequential.of("MyTest",
        Direct.of("test 1", context -> {
            })),
        Direct.of("test 2", context -> {
                throw new AssertionError("This fails");
            })),
        Direct.of("test 3", context -> {
                System.out.println("This never runs");
            })));
}
```

Output:
- test 1: `PASS`
- test 2: `FAIL`
- test 3: `SKIP`

Overall status: `FAIL`

Skipped children still fire listener `beforeAction()` and `afterAction()` callbacks, maintaining the execution contract.

## Sequential: Skip Propagation

`Sequential` continues executing even if a child skips:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Direct.of("test 1", context -> {
            })),
        Direct.of("test 2", context -> {
                throw SkipException.skip("Feature not available");
            })),
        Direct.of("test 3", context -> {
            })));
}
```

Output:
- test 1: `PASS`
- test 2: `SKIP`
- test 3: `PASS`

Overall status: `PASS`

## RandomSequential: Failure Propagation

`RandomSequential` runs all children even if some fail, in random order:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return RandomSequential.of("MyTest", 42L,
        Direct.of("test 1", context -> {
            })),
        Direct.of("test 2", context -> {
                throw new AssertionError("This fails");
            })),
        Direct.of("test 3", context -> {
            })));
}
```

All three tests run (order varies with seed 42). Output includes:
- One child: `PASS`
- One child: `FAIL`
- One child: `PASS`

Overall status: `FAIL`

All children execute regardless of failures. Use `StrictRandomSequential` for fail-fast behavior that stops on the first failure.

## StrictRandomSequential: Failure Propagation

`StrictRandomSequential` stops execution on the first failure (in random order) and skips remaining children:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return StrictRandomSequential.of("MyTest", 42L,
        Direct.of("test 1", context -> {
            })),
        Direct.of("test 2", context -> {
                throw new AssertionError("This fails");
            })),
        Direct.of("test 3", context -> {
            })));
}
```

All children are shuffled before execution. Once a failure occurs, remaining children are skipped. Output includes:
- Some children: `PASS` (depends on shuffle order)
- One child: `FAIL`
- Remaining children: `SKIP`

Overall status: `FAIL`

Skipped children still fire listener `beforeAction()` and `afterAction()` callbacks, maintaining the execution contract.

## Parallel: Failure Collection

`Parallel` executes all children and collects failures:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Parallel.of("MyTest", 2, List.of(
        Direct.of("test 1", context -> {
            })),
        Direct.of("test 2", context -> {
                throw new AssertionError("This fails");
            })),
        Direct.of("test 3", context -> {
            })),
        Direct.of("test 4", context -> {
                throw new RuntimeException("This also fails");
            })),
        Direct.of("test 5", context -> {
            }))));
}
```

All 5 tests run. Output:
- test 1: `PASS`
- test 2: `FAIL`
- test 3: `PASS`
- test 4: `FAIL`
- test 5: `PASS`

Overall status: `FAIL` (any failure causes overall failure)

## Parallel: Skip Propagation

`Parallel` executes all children even if some skip:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Parallel.of("MyTest", 2, List.of(
        Direct.of("test 1", context -> {
            })),
        Direct.of("test 2", context -> {
                throw SkipException.skip("Feature not available");
            })),
        Direct.of("test 3", context -> {
            }))));
}
```

All 3 tests run. Output:
- test 1: `PASS`
- test 2: `SKIP`
- test 3: `PASS`

Overall status: `PASS`

## Lifecycle: Teardown on Failure

`Lifecycle` teardown always runs, even if setup or body fail:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("MyTest",
        context -> {
            System.out.println("Setup");
            throw new RuntimeException("Setup fails");
        },
        Direct.of("test", context -> {
                System.out.println("This never runs");
            })),
        context -> {
            System.out.println("Teardown always runs");
        }));
}
```

Output:
```
Setup
Teardown always runs
```

Status: `FAIL` (setup failed)

## Lifecycle: Teardown Failure Suppression

If teardown fails after a previous failure, the teardown exception is suppressed:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("MyTest",
        context -> {
        },
        Direct.of("test", context -> {
                throw new RuntimeException("Body fails");
            })),
        context -> {
            throw new RuntimeException("Teardown also fails");
        }));
}
```

Primary failure: `RuntimeException: Body fails`
Suppressed failure: `RuntimeException: Teardown also fails`

Status: `FAIL`

## Lifecycle: Skip in Setup

If setup throws `SkipException`, body is skipped but teardown runs:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("MyTest",
        context -> {
            throw SkipException.skip("Feature not available");
        },
        Direct.of("test", context -> {
                System.out.println("This never runs");
            })),
        context -> {
            System.out.println("Teardown runs even on skip");
        }));
}
```

Output:
```
Teardown runs even on skip
```

Status: `SKIP`

## Accessing Failures

`Result` exposes the failure:

```java
Result result = runner.run(action);

if (result.status() == Result.Status.FAIL) {
    Optional<Throwable> failure = result.failure();
    if (failure.isPresent()) {
        System.out.println("Failed: " + failure.get());
    }
}
```

## Graceful Skip Pattern

Use `SkipException` to skip tests when required resources are unavailable:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Lifecycle.of("DatabaseTest",
        context -> {
            try {
                createDatabase();
            } catch (DatabaseUnavailableException e) {
                throw SkipException.skip("Database not available", e);
            }
        },
        Direct.of("test", context -> {
        })),
        context -> {
            dropDatabase();
        });
}
```

This pattern is commonly used in Testcontainers examples to skip tests when Docker is unavailable.

## See Also

- [Action Composition](action-composition) - How Sequential and Parallel handle failures
- [Lifecycle](../actions/lifecycle) - Teardown guarantees and skip behavior
- [Integration Testing](integration-testing) - Testcontainers graceful skip pattern
