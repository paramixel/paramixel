---
title: Project Setup
description: Module layout and discovery requirements for a Paramixel project.
---

# Project Setup

## Dependency

```xml
<dependency>
  <groupId>org.paramixel</groupId>
  <artifactId>core</artifactId>
  <version>7.0.0</version>
</dependency>
```

## Discovery requirements

- Factory methods are `public static`, take no parameters, and return `Task` or a builder.
- Return `null` to skip a factory explicitly.
- `@BeforeAll` / `@AfterAll` methods follow the same shape and wrap the discovered run.
- Test classes may live under `src/main/java` when the Maven plugin executes them during the `test` phase, or under
  `src/test/java` when the test classpath is used.

## Configuration

Add `src/main/resources/paramixel.properties` (or `src/test/resources`) for pool capacities and policies:

```properties
paramixel.parallelism.environments=@cores
paramixel.workers=8
```

## Module path

The `core` artifact is a named module (`org.paramixel.api`) that exports the public API packages. Internal `nonapi`
packages are not exported.

## Next

- [First definition](../getting-started/first-test)
- [Configuration properties](../configuration/properties)
