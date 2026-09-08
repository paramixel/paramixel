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
  <version>6.1.0</version>
</dependency>
```

Use the core dependency when running action trees programmatically or when compiling factory classes consumed by the Maven plugin.

## Maven plugin

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

The plugin goal prefix is `paramixel`; the executable goal is `paramixel:test` and it is bound to the Maven `test` phase when configured as above.

## Gradle

The repository's Gradle build runs Paramixel examples with a custom `JavaExec` task named `paramixelTest` whose main class is `org.paramixel.api.Runner`. If you add a similar task, put Paramixel core and your factory classes on the task classpath.

## Configuration file

Place optional defaults in `src/main/resources/paramixel.properties` or pass JVM system properties with `-D`.
