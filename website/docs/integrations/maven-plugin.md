---
title: Maven Plugin
description: Run Paramixel factories during the Maven test phase.
---

# Maven Plugin

The Maven plugin artifact is `org.paramixel:maven-plugin`; the goal prefix is `paramixel` and the main goal is
`paramixel:test`, bound to the `test` phase with test-scope dependency resolution.

```xml
<plugin>
  <groupId>org.paramixel</groupId>
  <artifactId>maven-plugin</artifactId>
  <version>7.0.0</version>
  <executions>
    <execution>
      <phase>test</phase>
      <goals>
        <goal>test</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

## Parameters

| Parameter | Property | Default |
| --- | --- | --- |
| `skipTests` | `paramixel.skipTests` | `false` |
| `failIfNoTests` | `paramixel.failIfNoTests` | `true` |
| `failureOnSkip` | `paramixel.failureOnSkip` | `true` |
| `failureOnAbort` | `paramixel.failureOnAbort` | `true` |
| `failFast` | `paramixel.failFast` | `false` |
| `reportFile` | `paramixel.report.file` | none |
| `matchPackage` | `paramixel.match.package.regex` | none |
| `matchClass` | `paramixel.match.class.regex` | none |
| `matchTag` | `paramixel.match.tag.regex` | none |
| `properties` | — | additional configuration keys |
| `strictThreadLifecycle` | `paramixel.strictThreadLifecycle` | `false` |

Configuration precedence, from lowest to highest: Paramixel defaults, the test-classpath `paramixel.properties`,
`<properties>` entries, explicitly set mojo parameters, and system properties. A parameter you do not set leaves the
lower layers in effect, so `paramixel.failFast` or `paramixel.match.class.regex` in `paramixel.properties` or
`<properties>` applies unless the matching parameter is configured.

The four runner flags `failIfNoTests`, `failureOnSkip`, `failureOnAbort`, and `failFast` accept `true` and `false`,
ignoring case and surrounding whitespace. Malformed effective values fail before factory invocation.

The mojo reports a failed or canceled run as `MojoFailureException`. Configuration, discovery, and plan errors become
`MojoExecutionException`, as does empty discovery with `failIfNoTests` enabled. With `strictThreadLifecycle`,
non-daemon threads that retain the test classloader are reported as an error rather than a warning.
