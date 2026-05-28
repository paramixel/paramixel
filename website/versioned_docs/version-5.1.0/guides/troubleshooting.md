---
title: Troubleshooting
description: Solutions to common Paramixel issues.
---

# Troubleshooting

## No tests discovered

When `Runner.run()` or the Maven plugin reports no Paramixel tests were found:

**Common causes:**

| Cause | Fix |
| --- | --- |
| Missing `@Paramixel.Factory` annotation | Add `@Paramixel.Factory` to a `public static` no-arg method returning `Spec<?>` or `Action<?>` |
| Factory method has parameters | Factory methods must take no arguments |
| Factory method is not `public static` | Factory methods must be `public` and `static` |
| Class not on the test classpath | Ensure the class is compiled and included in the test classpath (Maven: `src/main/java` in the examples module; see [Project Setup](../getting-started/project-setup)) |
| `@Paramixel.Disabled` on the factory method | Remove `@Disabled` to re-enable discovery |
| Selector filters too restrictive | Check `paramixel.match.package.regex`, `paramixel.match.class.regex`, and `paramixel.match.tag.regex` — regex uses `find()` semantics; for exact match use `^...$` |

**`failIfNoTests` behavior:**

- `failIfNoTests=false` (default): logs "No Paramixel tests found" and returns exit code `0`.
- `failIfNoTests=true`: logs to stderr and returns exit code `1`, failing the build.

**Null factory methods:** A `@Paramixel.Factory` method that returns `null` does not produce "no tests found." Instead, it creates a `Step` action that throws `SkipException`, resulting in a `SKIPPED` outcome.

## Classloader leaks after tests

When the Maven plugin warns or fails about lingering non-daemon threads:

```
Non-daemon threads are still running after Paramixel execution;
these threads may fail when the test classloader is closed:
```

**What the check does:** The plugin identifies non-daemon threads **whose context classloader is a `URLClassLoader`** — specifically, threads that retain a reference to the test classloader. Threads without this classloader reference are not considered lingering.

**Common sources of classloader leaks:**

| Source | Fix |
| --- | --- |
| Thread pools not shut down | Call `ExecutorService.shutdown()` in an `after` step or `AutoCloseable.close()` |
| `ThreadLocal` values not cleared | Clear thread locals in a cleanup step |
| Static `ScheduledExecutorService` | Make it an instance field managed by `Instance`, or shut down in `after` |
| `java.util.logging` handlers | Reset logging handlers in cleanup |

**`strictThreadLifecycle` modes:**

- `false` (default): logs a **WARN** message, build continues.
- `true`: throws `MojoExecutionException`, **build fails**.

## Parallel deadlocks with nested Parallel

Nested `Parallel` actions can deadlock if all scheduler worker threads are blocked waiting for child completions while child work items are queued but cannot be dequeued.

**How Paramixel avoids this:** The `AsyncScheduler` uses cooperative work-stealing in `managedJoin()` — threads waiting for child completions can execute queued work inline, preventing the deadlock.

**Guidelines:**

- Use `Parallel.parallelism(n)` to limit concurrency per action.
- Prefer `isIndependent()` for `Parallel` body children unless order matters.
- The framework's work-stealing mechanism handles typical nesting patterns. If you encounter a hang, verify that blocking operations (e.g., `CountDownLatch.await()`) are not preventing cooperative scheduling.

## Instance child ordering surprise

When mixing `Instance.child(String, ThrowingConsumer)` consumer-shorthand calls with `Instance.child(Spec<?>)` spec-based calls, the consumer-shorthand children are resolved **before** spec-based children regardless of call order.

**Example of non-obvious ordering:**

```java
Instance.of("test", MyService::new)
        .child("shorthand-A", MyService::testA)     // resolved first
        .child(specBasedB)                          // resolved second
        .child("shorthand-C", MyService::testC)     // resolved third, but appears BEFORE specBasedB
        .resolve();
```

Execution order: shorthand-A, shorthand-C, specBasedB.

**Fix:** Use a single overload type per spec:

```java
// All consumer shorthand (declaration order preserved)
Instance.of("test", MyService::new)
        .child("test-a", MyService::testA)
        .child("test-b", MyService::testB)
        .child("test-c", MyService::testC)
        .resolve();

// All spec-based (declaration order preserved)
Instance.of("test", MyService::new)
        .child(Step.of("test-a", MyService::testA))
        .child(Step.of("test-b", MyService::testB))
        .child(Step.of("test-c", MyService::testC))
        .resolve();
```

## Timeout not working as expected

`Timeout` uses wall-clock duration and cooperative thread interruption.

**What happens on timeout:**

1. The action records `FAILED` status with a timeout message.
2. The child thread is interrupted.
3. A 100ms grace period allows the child to respond to interruption.
4. If the child still hasn't completed after the grace period, the descriptor is force-set to `FAILED`.

**Common issues:**

| Issue | Cause | Fix |
| --- | --- | --- |
| Child continues after timeout | Child does not check `Thread.interrupted()` or is CPU-bound | Make the child cooperative (check interruption, use interruptible I/O) |
| Timeout too early | Duration includes scheduling overhead | Account for scheduler queue wait time in the timeout duration |
| Timeout doesn't fire | Child completed before the deadline | This is expected — timeout only fires when the child exceeds the deadline |

**Orphaned threads:** If the child ignores interruption, it becomes an orphaned daemon thread. Since the scheduler uses daemon threads, orphaned threads cannot prevent JVM shutdown.

## Report file not generated

**Check `paramixel.report.file`:**

- The path must be set for report generation to occur. When unset, no report file is generated.
- Tilde (`~`) expansion is supported on Linux/macOS: `~/reports/report.json` expands to `$HOME/reports/report.json`.
- The parent directory is created automatically if it doesn't exist.

**Report format is determined by file extension:**

| Extension | Format |
| --- | --- |
| `.json` | JSON |
| `.xml` | XML |
| `.html` / `.htm` | HTML |
| `.log`, `.txt`, other | Plain text |

**Common issues:**

| Issue | Fix |
| --- | --- |
| No report generated | Set `paramixel.report.file=target/paramixel/report.json` in POM config, system property, or `paramixel.properties` |
| Wrong format | Change the file extension to match the desired format |
| Permission denied | Ensure the process has write access to the target directory |
| Tilde not expanding on Windows | Use an absolute path instead: `C:\reports\report.json` |

## CI build fails intermittently

**Common causes:**

| Cause | Fix |
| --- | --- |
| `failIfNoTests=true` with selective test runs | Set `-Dparamixel.failIfNoTests=false` when filtering by tag/package on CI |
| Parallelism oversubscription on limited CI runners | Set `-Dparamixel.parallelism=2` or `Parallel.parallelism(2)` to match CI core count |
| Flaky external services | Use `Retry` with appropriate policy, or `Timeout` with generous deadlines |
| Race conditions in test state | Ensure shared state is thread-safe; prefer `Instance` for per-test isolation |
| `failureOnAbort=true` (default) with environment-dependent skips | Use `AbortedException` intentionally; consider `-Dparamixel.failureOnAbort=false` if skips are expected |

## Discovery finds wrong tests

Selector regex patterns use `find()` semantics (substring match), not `matches()` (full string match).

**Common mistakes:**

| Pattern | Matches | Intended | Fix |
| --- | --- | --- | --- |
| `com.example` | `com.example`, `com.example.tests`, `other.com.example` | Only `com.example` and subpackages | Use `Selector.packageTreeOf(ComExample.class)` or `^com\.example` |
| `MyTest` | `com.example.MyTest`, `com.other.MyTestExtension` | Only `com.example.MyTest` | Use `^com\.example\.MyTest$` or `Selector.classOf(MyTest.class)` |
| `smoke` | `smoke`, `smoke-tests`, `nosmoke` | Only `smoke` | Use `^smoke$` |

For exact matches, use the anchored helper methods: `Selector.packageOf()`, `Selector.classOf()`, or wrap regex with `^...$`.
