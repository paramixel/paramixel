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

The typed getters return optionals. Invalid numeric values and errors from `get(key, transformer)` throw
`ConfigurationException`. `keySet()` returns a set of property names. `Configuration.poolKey(name)` builds
`paramixel.parallelism.<name>`.

`getBoolean()` and `parseBoolean()` recognize `true` case-insensitively after stripping surrounding whitespace.
Other present values evaluate to `false`. The runner separately validates its four built-in boolean flags after
configuration precedence is applied.

Unknown `paramixel.*` keys are reported as warnings at run start. Secret-looking values are redacted from diagnostics and
reports.
