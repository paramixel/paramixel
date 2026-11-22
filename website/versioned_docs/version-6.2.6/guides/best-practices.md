---
title: Best Practices
description: Practical guidance for maintainable Paramixel suites.
---

# Best Practices

## Prefer explicit trees

Name every meaningful action so console output, reports, and descriptors are readable.

```java
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

sequential("checkout")
        .child(step("add item", ctx -> addItem()))
        .child(step("submit payment", ctx -> submitPayment()));
```

## Isolate parallel work

Parallel children should not mutate shared state unless synchronization is explicit and minimal.

## Use tags for intent

Use repeatable `@Paramixel.Tag` values such as `smoke`, `critical`, or `slow`. Select them with `paramixel.match.tag.regex` or `Selector.tagRegex(...)`.

## Keep configuration simple

Use `paramixel.properties` for defaults, system properties for CI overrides, and programmatic `Configuration` objects for custom runner setup.
