---
title: Maven Plugin
description: Run Paramixel factories during the Maven test phase.
---

# Maven Plugin

The Maven plugin artifact is `org.paramixel:maven-plugin`. Its goal prefix is `paramixel`, and the main goal is `paramixel:test`.

```xml
<plugin>
  <groupId>org.paramixel</groupId>
  <artifactId>maven-plugin</artifactId>
  <version>6.1.0</version>
  <executions>
    <execution>
      <goals>
        <goal>test</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

## Parameters

| Parameter | System property | Default | Description |
| --- | --- | --- | --- |
| `skipTests` | `paramixel.skipTests` | `false` | Skip Paramixel execution. |
| `failIfNoTests` | `paramixel.failIfNoTests` | `false` | Fail if no factories are discovered. |
| `failureOnSkip` | `paramixel.failureOnSkip` | `false` | Promote skipped aggregate results to build failure. |
| `failureOnAbort` | `paramixel.failureOnAbort` | `true` | Promote aborted aggregate results to build failure. |
| `failFast` | `paramixel.failFast` | `false` | Skip scheduling remaining root children after the first failure. |
| `reportFile` | `paramixel.report.file` | unset | Write a report file. |
| `matchPackage` | `paramixel.match.package.regex` | unset | Filter by package regex. |
| `matchClass` | `paramixel.match.class.regex` | unset | Filter by fully qualified class regex. |
| `matchTag` | `paramixel.match.tag.regex` | unset | Filter by tag regex. |
| `strictThreadLifecycle` | `paramixel.strictThreadLifecycle` | `false` | Enable strict thread lifecycle checks in plugin execution. |

## POM properties

The plugin also accepts a `<properties>` list of key/value pairs. Property precedence in the mojo is: Paramixel defaults, POM property declarations, the `reportFile` parameter, then system properties.

## Run examples

```bash
mvn paramixel:test
mvn test -Dparamixel.match.tag.regex=smoke
mvn test -Dparamixel.skipTests=true
```
