---
title: Configuration
description: Typed access to Paramixel properties.
---

# Configuration

```java
Configuration configuration = Configuration.defaultConfiguration();
Optional<Integer> workers = configuration.getInteger(Configuration.WORKERS);
Optional<Boolean> failFast = configuration.getBoolean(Configuration.FAIL_FAST);
```

## Factories

| Method | Layers |
| --- | --- |
| `defaultConfiguration()` | classpath `paramixel.properties` → system properties → defaults |
| `classpathConfiguration()` | classpath properties only |
| `systemConfiguration()` | system properties + defaults |
| `of(Map)` | an explicit map |

## Typed accessors

`getString`, `getBoolean`, `getInteger`, `getLong`, `get(key, transformer)`, and `keySet()` return optionals; an invalid
value throws `ConfigurationException`. `Configuration.poolKey(name)` builds `paramixel.parallelism.<name>`.

Unknown `paramixel.*` keys are reported as warnings at run start. Secret-looking values are redacted from diagnostics and
reports.
