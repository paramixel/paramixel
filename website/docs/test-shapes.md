---
title: Test Shapes
description: Common execution-tree patterns for Paramixel tests.
---

# Test Shapes

Paramixel is useful when a test has a shape that should be visible in code, execution, and reports. These patterns use existing 6.1.0 APIs only.

## Simple sequence

Use a sequence when steps must run in order.

```java
Action spec = Sequential.sequential("login smoke")
        .child(Step.of("open login page", ctx -> openLoginPage()))
        .child(Step.of("submit credentials", ctx -> submitCredentials()))
        .child(Assert.assertTrue("user is signed in", true))
        .build();
```

```text
login smoke
├── open login page
├── submit credentials
└── user is signed in
```

## Lifecycle with setup and teardown

Use a lifecycle shape when setup and cleanup belong to a specific subtree.

```java
Action spec = Scope.scope("database workflow")
        .before(Step.of("start database", ctx -> startDatabase()))
        .body(Sequential.sequential("checks")
                .child(Step.of("migrate schema", ctx -> migrateSchema()))
                .child(Step.of("verify query", ctx -> verifyQuery()))
                .build())
        .after(Step.of("stop database", ctx -> stopDatabase()))
        .build();
```

```text
database workflow
├── before: start database
├── body: checks
│   ├── migrate schema
│   └── verify query
└── after: stop database
```

## Parallel branches

Use parallel branches when independent scenarios can run concurrently.

```java
Action spec = Parallel.parallel("browser matrix")
        .parallelism(3)
        .child(Step.of("chrome", ctx -> runChrome()))
        .child(Step.of("firefox", ctx -> runFirefox()))
        .child(Step.of("webkit", ctx -> runWebkit()))
        .build();
```

```text
browser matrix
├── chrome
├── firefox
└── webkit
```

## Generated scenarios

Use generated scenarios when plain Java code should create many related actions.

```java
var users = List.of("ada", "grace", "linus");

Action spec = Each.sequential("user checks", users,
        user -> Sequential.sequential("check " + user)
                .child(Step.of("load", ctx -> loadUser(user)))
                .child(Step.of("verify", ctx -> verifyUser(user))))
        .build();
```

```text
user checks
├── check ada
│   ├── load
│   └── verify
├── check grace
│   ├── load
│   └── verify
└── check linus
    ├── load
    └── verify
```

## Compatibility-style tests

Use a compatibility shape when the same behavior must be checked across versions, configurations, or environments.

```java
var versions = List.of("Java 17", "Java 21", "Java 25");

Action spec = Each.parallel("compatibility matrix", versions,
        version -> Scope.scope(version + " scenario")
                .before(Step.of("start " + version, ctx -> start(version)))
                .body(Sequential.sequential("checks")
                        .child(Step.of("write", ctx -> write(version)))
                        .child(Step.of("read", ctx -> read(version))))
                .after(Step.of("stop " + version, ctx -> stop(version))))
        .parallelism(3)
        .build();
```

```text
compatibility matrix
├── Java 17 scenario
│   ├── before: start Java 17
│   ├── body: checks
│   │   ├── write
│   │   └── read
│   └── after: stop Java 17
├── Java 21 scenario
└── Java 25 scenario
```

## Failure handling

Use the default dependent sequence when later steps should stop after an earlier failure. Use an independent sequence when all children should run so the result tree can collect every outcome.

```java
Action failFast = Sequential.sequential("checkout")
        .child(Step.of("create cart", ctx -> createCart()))
        .child(Step.of("submit payment", ctx -> submitPayment()))
        .child(Step.of("verify receipt", ctx -> verifyReceipt()))
        .build();

Action collectAll = Sequential.sequential("cleanup checks")
        .independent()
        .child(Step.of("delete user", ctx -> deleteUser()))
        .child(Step.of("delete cart", ctx -> deleteCart()))
        .child(Step.of("delete invoice", ctx -> deleteInvoice()))
        .build();
```

```text
checkout
├── create cart
├── submit payment
└── verify receipt        (skipped if an earlier dependent child fails)

cleanup checks
├── delete user
├── delete cart
└── delete invoice        (all children are attempted)
```
