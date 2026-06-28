---
title: Configuration
description: Typed access to Paramixel configuration properties.
---

# Configuration

`Configuration` provides typed access to string-backed properties.

## Factory methods

- `Configuration.defaultConfiguration()` — classpath `paramixel.properties`, JVM system properties, then defaults.
- `Configuration.classpathConfiguration()` — classpath properties only.
- `Configuration.systemConfiguration()` — JVM system properties plus defaults.
- `Configuration.of(Map<String, String>)` — explicit map.

## Caching

`Configuration.defaultConfiguration()` caches the first result per classloader and returns
the cached copy on subsequent calls. This means runtime changes made after the first call
— for example, `System.setProperty("paramixel.parallelism", "2")` — are **not** reflected.

- To get a snapshot of the **current** system properties, call
  `Configuration.systemConfiguration()` instead.
- To construct a fresh configuration with overrides, use
  `Configuration.of(Map.of(...))` and pass it to
  `Runner.builder().configuration(configuration)`.
- If you need a reusable configuration that reflects live system properties, call
  `systemConfiguration()` each time rather than caching `defaultConfiguration()`.

TL;DR: `defaultConfiguration()` is a **one-time snapshot**. Do not expect it to
reflect mid-JVM property changes.

## Typed getters

`getString`, `getBoolean`, `getInteger`, `getLong`, `getFloat`, and `getDouble` return `Optional` values. Invalid numeric values throw `ConfigurationException`.

## Keys

| Key | Default | Description |
| --- | --- | --- |
| `paramixel.parallelism` | available processors | Runner-wide parallelism. Must be a positive integer. |
| `paramixel.scheduler.queue.capacity` | `1024` | Maximum scheduler-ready descriptor executions. Must be positive. |
| `paramixel.ansi` | `auto` | Console ANSI mode: `true`, `false`, or `auto`. |
| `paramixel.failureOnSkip` | `false` | Treat skipped aggregate results as failing exit codes. |
| `paramixel.failureOnAbort` | `true` | Treat aborted aggregate results as failing exit codes. |
| `paramixel.failIfNoTests` | `false` | Fail when classpath discovery finds no factories. |
| `paramixel.failFast` | `false` | Stop scheduling remaining root children after the first failed or aborted action. |
| `paramixel.report.file` | unset | Write a run report. `.json`, `.xml`, `.html`, `.log`, and `.txt` are recognized; unknown extensions use text. |
| `paramixel.match.package.regex` | unset | Java regex, using `matches()`, matched against package names. |
| `paramixel.match.class.regex` | unset | Java regex, using `matches()`, matched against fully qualified class names. |
| `paramixel.match.tag.regex` | unset | Java regex, using `matches()`, matched against each `@Paramixel.Tag` value. |
| `paramixel.listener.exclude` | unset | Comma-separated listener sections: `status.header`, `status.footer`, `summary.header`, `summary.tree`, `summary.footer`, plus shorthands `status`, `quiet`, and `all`. |
