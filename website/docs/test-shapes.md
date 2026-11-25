---
title: Test Shapes
description: Reusable definition shapes for common integration testing problems.
---

# Test Shapes

## Linear flow

```java
Task definition = sequential(LoginSmokeTest.class, "login smoke")
        .step("openLoginPage")
        .step("submitCredentials")
        .step("assertSignedIn")
        .build();
```

Each step is a child task. A failing step skips the rest with an explicit reason.

## Fixture with setup and teardown

```java
Task definition = sequential(DatabaseWorkflowTest.class, "database workflow")
        .bind(DSN, dsn)
        .before("migrate")
        .step("insert")
        .step("query")
        .after("dropSchema")
        .build();
```

The fixture is created lazily per attempt, `after` runs even when a step fails, and nothing is closed implicitly.

## Compatibility matrix

```java
final var root = parallel(MySuite.class, "compatibility matrix", Pool.named("environments"));
for (final var environment : environments) {
    root.child(sequential(environment.name())
            .bind(ENVIRONMENT, environment)
            .before("start")
            .step("verify")
            .after("stop"));
}
Task definition = root.build();
```

A failure in one environment does not cancel the others; the group aggregates the most severe outcome.

## Checks that should all run

```java
Task definition = sequential("validation")
        .independent()
        .step("check A", Test::checkA)
        .step("check B", Test::checkB)
        .build();
```

`independent()` continues after a failure but still runs one step at a time and still aggregates the failure.

## Polling with a bound

```java
Task definition = repeat("readiness", poll -> poll
        .maxIterations(10)
        .untilPassed()
        .delay(Backoff.exponential(Duration.ofMillis(100), Duration.ofSeconds(2)))
        .step("probe", Probe::assertReady))
        .build();
```

## Isolated resource access

```java
Task definition = parallel("matrix", Pool.named("environments"))
        .child(sequential("group-a").isolated("db").step("seed", Db::seed))
        .child(sequential("group-b").isolated("db").step("seed", Db::seed))
        .build();
```

Both groups run, but their bodies never overlap.

## Fail-fast at the root

`paramixel.failFast=true` stops admitting new root tasks after a root task fails (`FAILED`, which includes a timeout).
A deliberate skip (`throw skip(...)` or a false `when`/`onlyIf` condition) and an assumption abort (`throw abort(...)`)
are not failures and do not trigger it. Already-running work finishes; roots that are not admitted are `SKIPPED` with an
explicit `failFast` reason.
