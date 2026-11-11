---
title: Test Shapes
description: Reusable definition shapes for common integration testing problems.
---

# Test Shapes

## Linear flow

```java
Task definition = sequential("login smoke")
        .step("open login page", Browser::openLoginPage)
        .step("submit credentials", Browser::submitCredentials)
        .step("user is signed in", Browser::assertSignedIn)
        .build();
```

Each step is a child task. A failing step skips the rest with an explicit reason.

## Fixture with setup and teardown

```java
Task definition = forInstance("database workflow", () -> new DatabaseWorkflow(dsn))
        .before(DatabaseWorkflow::migrate)
        .step("insert", DatabaseWorkflow::insert)
        .step("query", DatabaseWorkflow::query)
        .after(DatabaseWorkflow::dropSchema)
        .build();
```

The fixture is created lazily per attempt, `after` runs even when a step fails, and nothing is closed implicitly.

## Compatibility matrix

```java
Task definition = parallel("compatibility matrix", Pool.named("environments"))
        .each(environments, env -> forInstance(env.name(), () -> new Probe(env))
                .before(Probe::start)
                .step("verify", Probe::verify)
                .after(Probe::stop))
        .build();
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

`paramixel.failFast=true` stops admitting new root tasks after a non-passing root outcome. Already-running work finishes;
skipped roots carry an explicit reason.
