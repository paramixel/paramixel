---
title: Release Notes
description: Current documentation-facing release notes.
---

# Release Notes

## Unreleased
(No unreleased changes)

## 6.0.0 documentation baseline

These docs reflect the current source tree:

- Java 17+ core API under `org.paramixel.api`.
- Action trees are composable execution units executed by `Runner`.
- Configuration uses `paramixel.properties`, JVM system properties, and programmatic `Configuration` objects.
- Maven plugin artifact is `org.paramixel:maven-plugin` with goal prefix `paramixel` and goal `test`.
- Gradle usage is via a `JavaExec` task that runs `org.paramixel.api.Runner`.
- Reports are enabled with `paramixel.report.file`.

Migration guides are available for upgrading from 1.x, 2.x, 3.x, 4.x, 5.0.x, and 5.1.x directly to 6.0.0.
