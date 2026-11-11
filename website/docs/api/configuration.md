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

`getString`, `getBoolean`, `getInteger` and `getLong` return `Optional` values, and `get(key, transformer)` covers anything else (for example `get("ratio", Double::valueOf)`). Invalid values throw `ConfigurationException`.

## Keys

| Key | Default | Description |
| --- | --- | --- |
| `paramixel.parallelism` | available processors | Size of the root pool: the discovered top-level `Parallel`. Must be a positive integer. |
| `paramixel.parallelism.<pool>` | none | Size of the user pool `<pool>`, which every `Parallel` that names it must find defined; a pool may be called `root`. A positive integer, or `@cores`, `@cores*N`, `@cores/N` (see below). Use `Configuration.poolKey(name)`. |
| `paramixel.ansi` | `auto` | Console ANSI mode: `true`, `false`, or `auto`. |
| `paramixel.failureOnSkip` | `false` | Treat skipped aggregate results as failing exit codes. |
| `paramixel.failureOnAbort` | `true` | Treat aborted aggregate results as failing exit codes. |
| `paramixel.failIfNoTests` | `false` | Fail when classpath discovery finds no factories. |
| `paramixel.failFast` | `false` | Skip the remaining direct children of the root after the first failed or aborted action. |
| `paramixel.report.file` | unset | Write a run report. `.json`, `.xml`, `.html`, `.log`, and `.txt` are recognized; unknown extensions use text. |
| `paramixel.match.package.regex` | unset | Java regex, using `matches()`, matched against package names. |
| `paramixel.match.class.regex` | unset | Java regex, using `matches()`, matched against fully qualified class names. |
| `paramixel.match.tag.regex` | unset | Java regex, using `matches()`, matched against each `@Paramixel.Tag` value. |
| `paramixel.listener.exclude` | unset | Comma-separated listener sections: `status.header`, `status.footer`, `summary.header`, `summary.tree`, `summary.footer`, plus shorthands `status`, `quiet`, and `all`. |

An unknown key that starts with `paramixel.` is reported once through `Listener.onWarning`, as is a
`paramixel.parallelism.<name>` key that names a pool no `Parallel` in the run uses.

## Sizing a pool from the CPU count

A pool size (and `paramixel.parallelism`) is a positive integer or one of:

| Value | Meaning |
| --- | --- |
| `@cores` | the number of processors the JVM reports (`Runtime.availableProcessors()`, which honors container CPU limits) |
| `@cores*N` | that number times `N` |
| `@cores/N` | that number divided by `N`, rounded down |

`N` is a positive integer, and the result is never less than 1. It is resolved once per run. Any other value fails
the run before anything executes and names the key.

```properties
# start at most as many environments as the JVM reports cores
paramixel.parallelism.environments=@cores
```
