---
title: Profiles
description: Model local, CI, smoke, and full runs with properties and selectors.
---

# Profiles

Model local, CI, smoke, and full profiles with Maven or Gradle build profiles, JVM system properties, multiple resource sets, or Paramixel tag selectors.

## Local run

```bash
mvn test -Dparamixel.parallelism=2 -Dparamixel.listener.exclude=quiet
```

## CI run

```bash
mvn test \
  -Dparamixel.parallelism=8 \
  -Dparamixel.failureOnSkip=true \
  -Dparamixel.report.file=target/paramixel-report.json
```

## Smoke run

```bash
mvn test -Dparamixel.match.tag.regex=smoke
```

## Programmatic profile

```java
import java.util.Map;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;

var configuration = Configuration.of(Map.of(
        "paramixel.parallelism", "4",
        "paramixel.match.tag.regex", "critical"));

var runner = Runner.builder().configuration(configuration).build();
```
