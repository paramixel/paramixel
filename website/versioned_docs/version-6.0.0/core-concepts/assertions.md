---
title: Assertions
description: Assertion actions and failure status behavior.
---

# Assertions

Paramixel includes `Assert` for boolean condition checks:

```java
import org.paramixel.api.action.Assert;

var positive = Assert.of("cart has item", true, () -> cart.hasItem(), "cart should contain an item");
var negative = Assert.of("no errors", true, () -> !page.hasErrors(), "page should not show errors");
```

`Assert.of(name, expected, actualSupplier)` passes when the condition equals the expected value. The `BooleanSupplier` is evaluated lazily on each execution. Static-value overloads and variants with custom failure messages are also available:

```java
// Static value
Assert.of("check", true, actual);

// Static value with message
Assert.of("check", true, actual, "expected true");

// Lazy supplier with message
Assert.of("check", true, () -> computeActual(), "expected true");
```

Failed assertions throw `FailException`, which the scheduler maps to a failed terminal outcome and includes in the aggregate run result.

For richer assertions, use `Step` with your assertion library:

```java
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

Sequence.builder("api check")
        .child(Step.of("status is 200", ctx -> assertThat(response.statusCode()).isEqualTo(200)))
        .build();
```

Thrown exceptions are mapped to terminal statuses by the scheduler. Use `SkipException`, `AbortedException`, or `FailException` when code needs to communicate an explicit outcome.
