---
title: Properties
description: paramixel.properties keys, defaults, and precedence.
---

# Properties

Configuration is layered: classpath `paramixel.properties`, then JVM system properties, then built-in defaults. A runner
may add a final layer for pool capacities via `Runner.Builder.pool(Pool, int)`.

| Key | Purpose | Default |
| --- | --- | --- |
| `paramixel.parallelism` | Admission budget for discovered root tasks; default capacity of the `discovery` pool | processors |
| `paramixel.parallelism.<name>` | Capacity of the named pool `<name>` | none (required) |
| `paramixel.workers` | Maximum execution worker threads | processors |
| `paramixel.shutdown.graceMillis` | Bounded wait for a canceled callback to quiesce | `10000` |
| `paramixel.listener.queue.capacity` | Bounded listener event queue | `8192` |
| `paramixel.ansi` | Console color: `true`, `false`, `auto` | `auto` |
| `paramixel.failureOnSkip` | Map a skipped run to a failing exit code | `true` |
| `paramixel.failureOnAbort` | Map an aborted run to a failing exit code | `true` |
| `paramixel.failIfNoTests` | Fail when discovery finds nothing | `true` |
| `paramixel.failFast` | Stop admitting new root tasks after a root failure | `false` |
| `paramixel.report.file` | Report file; format follows the extension | none |
| `paramixel.match.package.regex` | Discovery package filter | none |
| `paramixel.match.class.regex` | Discovery class filter | none |
| `paramixel.match.tag.regex` | Discovery tag filter | none |
| `paramixel.listener.exclude` | Console sections to suppress | none |

## Pool expressions

A capacity is a positive integer, or `@cores`, `@cores*N`, `@cores/N`, resolved once against
`Runtime.availableProcessors()` and never below one.

```properties
paramixel.parallelism.environments=3
paramixel.parallelism.checks=@cores*2
```

## Validation

An unknown `paramixel.*` key is reported as a warning. Invalid values fail before any test code runs:
`ConfigurationException` for configuration, `CompilationException` for a plan (including an undefined pool).

## Example

```properties
paramixel.workers=8
paramixel.parallelism=4
paramixel.parallelism.environments=@cores
paramixel.parallelism.checks=16
paramixel.failureOnSkip=true
paramixel.report.file=target/paramixel-report.json
```
