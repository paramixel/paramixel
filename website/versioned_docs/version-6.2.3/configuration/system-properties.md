---
title: System Properties
description: Override Paramixel configuration with JVM system properties.
---

# System Properties

JVM system properties override classpath `paramixel.properties` when using `Configuration.defaultConfiguration()`.

```bash
mvn test \
  -Dparamixel.parallelism=4 \
  -Dparamixel.match.tag.regex=smoke \
  -Dparamixel.report.file=target/paramixel-smoke.json
```

## Plugin properties

The Maven plugin also maps these properties directly to mojo parameters:

- `paramixel.skipTests`
- `paramixel.failIfNoTests`
- `paramixel.failureOnSkip`
- `paramixel.failureOnAbort`
- `paramixel.failFast`
- `paramixel.report.file`
- `paramixel.match.package.regex`
- `paramixel.match.class.regex`
- `paramixel.match.tag.regex`
- `paramixel.strictThreadLifecycle`

`Configuration.systemConfiguration()` copies all JVM system properties into the configuration and then applies built-in defaults. Avoid exposing the full key set in logs because JVM properties can contain sensitive values.

See [Configuration API](../api/configuration) for the complete list of supported keys and their defaults.
