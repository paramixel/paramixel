---
title: Test Shapes
description: Common execution-tree patterns for Paramixel tests.
---

# Test Shapes

Paramixel is useful when a test has a shape that should be visible in code, execution, and reports. These patterns use existing 6.1.0 APIs only.

## Simple sequence

Use a sequence when steps must run in order.

```java
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

Action spec = sequential("login smoke")
        .child(step("open login page", ctx -> openLoginPage()))
        .child(step("submit credentials", ctx -> submitCredentials()))
        .child(step("user is signed in", ctx -> {
                    if (!isSignedIn()) {
                        throw fail("user is not signed in");
                    }
                }))
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
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

Action spec = scope("database workflow")
        .before(step("start database", ctx -> startDatabase()))
        .body(sequential("checks")
                .child(step("migrate schema", ctx -> migrateSchema()))
                .child(step("verify query", ctx -> verifyQuery()))
                .build())
        .after(step("stop database", ctx -> stopDatabase()))
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
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

Action spec = parallel("browser matrix", "examples")
        .child(step("chrome", ctx -> runChrome()))
        .child(step("firefox", ctx -> runFirefox()))
        .child(step("webkit", ctx -> runWebkit()))
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
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

var users = List.of("ada", "grace", "linus");

Action spec = sequential("user checks")
        .each(users, user -> sequential("check " + user)
                .child(step("load " + user, ctx -> loadUser(user)))
                .child(step("verify " + user, ctx -> verifyUser(user))))
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
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

var versions = List.of("Java 17", "Java 21", "Java 25");

Action spec = parallel("compatibility matrix", "test")
        .each(versions, version -> of(version + " scenario", CompatibilityFixture.class,
                        () -> new CompatibilityFixture(version))
                .before("start " + version, CompatibilityFixture::start)
                .body(sequential("checks")
                        .child(step("write " + version, CompatibilityFixture.class, CompatibilityFixture::write))
                        .child(step("read " + version, CompatibilityFixture.class, CompatibilityFixture::read)))
                .after("stop " + version, CompatibilityFixture::stop))
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
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.exception.FailException.fail;

Action failFast = sequential("checkout")
        .child(step("create cart", ctx -> createCart()))
        .child(step("submit payment", ctx -> submitPayment()))
        .child(step("verify receipt", ctx -> verifyReceipt()))
        .build();

Action collectAll = sequential("cleanup checks")
        .independent()
        .child(step("delete user", ctx -> deleteUser()))
        .child(step("delete cart", ctx -> deleteCart()))
        .child(step("delete invoice", ctx -> deleteInvoice()))
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
