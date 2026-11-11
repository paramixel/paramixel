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
| `matchPackage` / `matchClass` / `matchTag` | `paramixel.match.*` | none |
| `properties` | — | additional configuration keys |
| `strictThreadLifecycle` | `paramixel.strictThreadLifecycle` | `false` |

The mojo never calls `System.exit`: a non-zero result exit code becomes a `MojoFailureException`, and configuration,
discovery, or plan errors become a `MojoExecutionException`. With `strictThreadLifecycle`, non-daemon threads that retain
the test classloader are reported as an error rather than a warning.
