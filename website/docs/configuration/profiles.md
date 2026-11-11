---
title: Profiles and Environments
description: Adapting pool sizes and policies per environment.
---

# Profiles and Environments

Paramixel reads the same keys from any environment, so environment-specific tuning is a matter of where the properties
come from.

## CI versus local

```properties
# src/main/resources/paramixel.properties (local default)
paramixel.parallelism.environments=4
paramixel.shutdown.graceMillis=10000
```

```bash
# CI: fewer containers, tighter deadlines
./mvnw test -pl examples -Dparamixel.parallelism.environments=1 -Dparamixel.shutdown.graceMillis=5000
```

## Maven profiles

The Maven plugin accepts `<properties>` plus explicit parameters (`failIfNoTests`, `failureOnSkip`, `failureOnAbort`,
`failFast`, `reportFile`, `match*`). A profile can set either.

## Guidelines

- Size pools for the protected resource (containers, ports, licenses), not for CPU count.
- Keep `paramixel.workers` at or above the largest pool you expect to saturate.
- Do not hide `failureOnSkip`/`failureOnAbort` differences between environments; local and CI should agree on what fails.
