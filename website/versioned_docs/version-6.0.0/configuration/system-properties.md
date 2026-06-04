---
title: System Properties
description: Override Paramixel configuration with JVM system properties.
---

# System Properties

JVM system properties override classpath `paramixel.properties` when using `Configuration.defaultConfiguration()`.

```bash
mvn test   -Dparamixel.parallelism=4   -Dparamixel.match.tag.regex=smoke   -Dparamixel.report.file=target/paramixel-smoke.json
```

## Plugin properties

The Maven plugin also maps these properties directly to mojo parameters:

- `paramixel.skipTests`
- `paramixel.failIfNoTests`
- `paramixel.failureOnSkip`
- `paramixel.failureOnAbort`
- `paramixel.report.file`
- `paramixel.match.package.regex`
- `paramixel.match.class.regex`
- `paramixel.match.tag.regex`
- `paramixel.strictThreadLifecycle`

## Supported configuration keys

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

`Configuration.systemConfiguration()` copies all JVM system properties into the configuration and then applies built-in defaults. Avoid exposing the full key set in logs because JVM properties can contain sensitive values.
