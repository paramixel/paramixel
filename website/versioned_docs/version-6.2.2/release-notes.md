---
title: Release Notes
description: Current documentation-facing release notes.
---

# Release Notes

## Unreleased
- **`Conditional.reason()` removed** — The configurable skip reason has been removed
  from `Conditional` and `Conditional.Builder`. When the predicate returns `false`,
  the skip-status message now includes the conditional's display name. Use a
  descriptive display name instead of a separate reason message.

## 6.2.1

- **Bug fix: regex selector matching** — `Selector.packageRegex()`, `Selector.classRegex()`, `Selector.tagRegex()`,
  and the `paramixel.match.*.regex` configuration keys incorrectly used `Pattern.find()` (substring match) instead of
  `Pattern.matches()` (full-string match). The `match` in the configuration key names specifies the intended contract;
  the implementation did not honor it. This is now fixed. Patterns that previously relied on the incorrect substring
  behavior must be updated with wildcards (e.g., `.*smoke.*`). See
  [Migration 6.2.x to 6.2.1](./guides/migration-6.2-to-6.2.1) for details.

## 6.0.0 documentation baseline

These docs reflect the current source tree:

- Java 17+ core API under `org.paramixel.api`.
- Action trees are composable execution units executed by `Runner`.
- Configuration uses `paramixel.properties`, JVM system properties, and programmatic `Configuration` objects.
- Maven plugin artifact is `org.paramixel:maven-plugin` with goal prefix `paramixel` and goal `test`.
- Gradle usage is via a `JavaExec` task that runs `org.paramixel.api.Runner`.
- Reports are enabled with `paramixel.report.file`.

Migration guides are available for upgrading from 1.x, 2.x, 3.x, 4.x, 5.0.x, and 5.1.x directly to 6.0.0.
