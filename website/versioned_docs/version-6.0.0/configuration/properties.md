---
title: Configuration Properties
description: Paramixel properties, defaults, and precedence.
---

# Configuration Properties

Paramixel configuration is properties-based. The classpath resource name is `paramixel.properties`.

## Precedence

For `Configuration.defaultConfiguration()`, values are layered in this order:

1. `paramixel.properties` from the classpath
2. JVM system properties
3. built-in defaults for unset framework keys

Later layers override earlier layers.

## Supported keys

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

## Example

```properties
paramixel.parallelism=4
paramixel.failureOnAbort=true
paramixel.report.file=target/paramixel-report.html
paramixel.listener.exclude=quiet
```

Use `paramixel.properties` for classpath defaults, JVM `-D` system properties for environment-specific overrides, or programmatic `Configuration` objects when constructing a `Runner`.
