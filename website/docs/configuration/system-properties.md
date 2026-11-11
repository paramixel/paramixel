---
title: System Properties
description: Overriding configuration from the command line.
---

# System Properties

JVM system properties override `paramixel.properties`, so any key can be set at launch:

```bash
./mvnw test -pl examples \
  -Dparamixel.parallelism=2 \
  -Dparamixel.parallelism.environments=1 \
  -Dparamixel.workers=4
```

`Runner.Builder.pool(Pool, int)` overrides both for that run.

## Precedence

1. `Runner.Builder.pool(Pool, capacity)`
2. System property
3. `paramixel.properties` on the classpath
4. Built-in default

## Security note

`Configuration.defaultConfiguration()` and `systemConfiguration()` copy every JVM system property into the returned
configuration. Paramixel only reads `paramixel.*` keys for behavior, redacts secret-looking values in diagnostics and
reports, and never writes the full property set to a report. Avoid placing credentials in `paramixel.*` keys.
