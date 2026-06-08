---
title: Release Notes
description: Current documentation-facing release notes.
---

# Release Notes

## 6.1.0

- **`Conditional` action** — A new composite action that evaluates a `Predicate<Context>` before executing its body. When the predicate returns `false`, the entire body subtree reports `SKIPPED` with a configurable reason message. Build with `Conditional.builder(name, predicate).body(action).reason("...").build()`.
- **`@BeforeAll` and `@AfterAll` annotations** — New runner lifecycle hooks discovered during classpath scanning. `@BeforeAll` methods execute once before any discovered test actions; `@AfterAll` methods execute once after all tests complete. Both return `Action`/`Builder`, same signature as `@Factory`. Hooks are ordered by `@Priority` on their declaring class. When hooks are present, the root action is wrapped in a `Static` with before/after sequences.

## Unreleased
(No unreleased changes)

## 6.1.0 documentation baseline

These docs reflect the current source tree:

- Java 17+ core API under `org.paramixel.api`.
- Action trees are composable execution units executed by `Runner`.
- Configuration uses `paramixel.properties`, JVM system properties, and programmatic `Configuration` objects.
- Maven plugin artifact is `org.paramixel:maven-plugin` with goal prefix `paramixel` and goal `test`.
- Gradle usage is via a `JavaExec` task that runs `org.paramixel.api.Runner`.
- Reports are enabled with `paramixel.report.file`.

Migration guides are available for upgrading from 1.x, 2.x, 3.x, 4.x, 5.0.x, and 5.1.x directly to 6.x.
