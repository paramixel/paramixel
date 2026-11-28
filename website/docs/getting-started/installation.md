---
title: Installation
description: Add Paramixel core and the Maven plugin to a Java 17 project.
---

# Installation

Paramixel requires Java 17 or newer.

## Core dependency

```xml
<dependency>
  <groupId>org.paramixel</groupId>
  <artifactId>core</artifactId>
  <version>7.0.0</version>
</dependency>
```

Use the core dependency when running task definitions programmatically or when compiling factory classes consumed by the Maven plugin.

## Maven plugin

```xml
<plugin>
  <groupId>org.paramixel</groupId>
  <artifactId>maven-plugin</artifactId>
  <version>7.0.0</version>
  <executions>
    <execution>
      <goals>
        <goal>test</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

The plugin goal prefix is `paramixel`; the executable goal is `paramixel:test` and it is bound to the Maven `test` phase when configured as above.

## Gradle

Run the repository's examples with `./gradlew :examples:paramixelTest`. This `JavaExec` task starts
`org.paramixel.api.Launcher` with Paramixel core and the example factories on its classpath.

## Configuration file

Place optional defaults in `src/main/resources/paramixel.properties` or pass JVM system properties with `-D`.
