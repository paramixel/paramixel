---
title: Quick Reference
description: Common APIs, annotations, and configuration keys.
---

# Quick Reference

## Minimal factory

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

@Paramixel.Factory
public static Action smoke() {
    return sequential("smoke")
            .child(step("step", ctx -> doWork()))
            .build();
}
```

## Using named builders

For shorter action tree definitions, use the named builder factories:

```java
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

@Paramixel.Factory
public static Action smoke() {
    return sequential("smoke")
        .child(step("arrange", ctx -> arrange()))
        .child(step("act", ctx -> act()))
        .child(step("assert", ctx -> assertResult()))
        .build();
}
```

See [Named Builders](../api/named-builders) for the full method reference.


## Annotations

| Annotation | Target | Purpose |
| --- | --- | --- |
| `@Paramixel.Factory` | method | Marks a public static factory returning `Action`, `Builder`, or `null`. |
| `@Paramixel.BeforeAll` | method | Marks a public static runner-level hook that executes once before discovered test actions. |
| `@Paramixel.AfterAll` | method | Marks a public static runner-level hook that executes once after discovered test actions. |
| `@Paramixel.Disabled` | method | Excludes a factory or hook from discovery. |
| `@Paramixel.Tag` | method | Repeatable tag used by selectors and regex filters. |
| `@Paramixel.Priority` | class | Orders discovered classes before root action construction. |
| `@Paramixel.Id` | method | Identifies methods for `AnnotationResolver`. |

Use `@Paramixel.BeforeAll` and `@Paramixel.AfterAll` for runner-wide lifecycle around all discovered factory actions. Use `Scope` for setup and teardown that belongs to one test or action tree.

## Configuration

See [Configuration API](../api/configuration) for the complete list of supported configuration keys with defaults and descriptions.

## Maven

```bash
mvn paramixel:test
mvn test -Dparamixel.match.tag.regex=smoke
```
