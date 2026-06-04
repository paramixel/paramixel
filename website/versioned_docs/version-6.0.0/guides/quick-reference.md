---
title: Quick Reference
description: Common APIs, annotations, and configuration keys.
---

# Quick Reference

## Minimal factory

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

@Paramixel.Factory
public static Action smoke() {
    return Sequence.builder("smoke")
            .child(Step.of("step", ctx -> doWork()))
            .build();
}
```

## Annotations

| Annotation | Target | Purpose |
| --- | --- | --- |
| `@Paramixel.Factory` | method | Marks a public static factory returning `Action`, `Builder`, or `null`. |
| `@Paramixel.Disabled` | method | Excludes a factory from discovery. |
| `@Paramixel.Tag` | method | Repeatable tag used by selectors and regex filters. |
| `@Paramixel.Priority` | class | Orders discovered factory classes before root construction. |
| `@Paramixel.Id` | method | Identifies methods for `AnnotationResolver`. |

## Configuration

| Key | Default | Description |
| --- | --- | --- |
| `paramixel.parallelism` | available processors | Runner-wide parallelism. Must be a positive integer. |
| `paramixel.scheduler.queue.capacity` | `1024` | Maximum scheduler-ready descriptor executions. Must be positive. |
| `paramixel.ansi` | `auto` | Console ANSI mode: `true`, `false`, or `auto`. |
| `paramixel.failureOnSkip` | `false` | Treat skipped aggregate results as failing exit codes. |
| `paramixel.failureOnAbort` | `true` | Treat aborted aggregate results as failing exit codes. |
| `paramixel.failIfNoTests` | `false` | Fail when classpath discovery finds no factories. |
| `paramixel.report.file` | unset | Write a run report. `.json`, `.xml`, `.html`, `.log`, and `.txt` are recognized; unknown extensions use text. |
| `paramixel.match.package.regex` | unset | Java regex, using `find()`, matched against package names. |
| `paramixel.match.class.regex` | unset | Java regex, using `find()`, matched against fully qualified class names. |
| `paramixel.match.tag.regex` | unset | Java regex, using `find()`, matched against each `@Paramixel.Tag` value. |
| `paramixel.listener.exclude` | unset | Comma-separated listener sections: `status.header`, `status.footer`, `summary.header`, `summary.tree`, `summary.footer`, plus shorthands `status`, `quiet`, and `all`. |

## Maven

```bash
mvn paramixel:test
mvn test -Dparamixel.match.tag.regex=smoke
```
