---
title: Project Setup
description: Recommended project layout for Paramixel tests.
---

# Project Setup

A typical Maven project keeps factory classes on the test classpath and runs them during the `test` phase with the Paramixel Maven plugin.

```text
src/
  test/
    java/
      com/example/LoginSmokeTest.java
    resources/
      paramixel.properties
```

## Factory conventions

- Factories are public static methods annotated with `@Paramixel.Factory`.
- Factories return `Action`, `Builder`, or `null`.
- Use `@Paramixel.Tag` for selection.
- Use `@Paramixel.Disabled` to exclude a factory from discovery.
- Use `@Paramixel.Priority` on classes when discovery admission order matters.

## Example properties

```properties
paramixel.parallelism=4
paramixel.match.tag.regex=smoke|critical
paramixel.report.file=target/paramixel-report.json
```

System properties override classpath properties:

```bash
mvn test -Dparamixel.match.class.regex=Login -Dparamixel.report.file=target/login.json
```
