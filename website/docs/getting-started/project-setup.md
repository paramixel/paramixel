---
title: Project Setup
description: Set up a Paramixel project with Maven or Gradle.
---

# Project Setup

This page covers project structure conventions and build configuration for Paramixel projects.

## Project structure

### Maven

```
my-project/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/example/
    │           └── MyTest.java
    └── test/
        └── resources/
            └── paramixel.properties
```

Paramixel tests live under `src/main/java/` (not `src/test/java/`) because the Paramixel Maven plugin discovers them from the test classpath during the `test` phase. The plugin builds a classloader from the project's test classpath, which includes main output, test output, and test dependencies.

### Gradle

```
my-project/
├── build.gradle
├── settings.gradle
└── src/
    └── paramixel/
        ├── java/
        │   └── com/example/
        │       └── MyTest.java
        └── resources/
            └── paramixel.properties
```

Paramixel tests live under `src/paramixel/java/` using a custom `sourceSets` block. The `paramixelTest` task runs them via `JavaExec` with `Runner.main()`.

## Maven configuration

### Minimal

```xml
<properties>
    <paramixel.version>5.0.0</paramixel.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.paramixel</groupId>
        <artifactId>core</artifactId>
        <version>${paramixel.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.paramixel</groupId>
            <artifactId>maven-plugin</artifactId>
            <version>${paramixel.version}</version>
            <executions>
                <execution>
                    <goals>
                        <goal>test</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### With report file

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <configuration>
        <reportFile>${project.build.directory}/paramixel/paramixel.json</reportFile>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>test</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### With custom properties

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <configuration>
        <reportFile>${project.build.directory}/paramixel/paramixel.json</reportFile>
        <properties>
            <property>
                <key>paramixel.parallelism</key>
                <value>4</value>
            </property>
        </properties>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>test</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Gradle configuration

### Full example

```groovy
plugins {
    id 'java'
}

sourceSets {
    paramixel {
        java {
            srcDir 'src/paramixel/java'
        }
        resources {
            srcDir 'src/paramixel/resources'
        }
    }
}

dependencies {
    paramixelImplementation 'org.paramixel:core:5.0.0'
}

tasks.register('paramixelTest', JavaExec) {
    dependsOn 'paramixelClasses'
    group = 'verification'
    description = 'Discovers and executes Paramixel action trees'
    mainClass = 'org.paramixel.api.Runner'
    classpath = sourceSets.paramixel.runtimeClasspath
}

tasks.named('check').configure {
    dependsOn 'paramixelTest'
}
```

### Skipping Paramixel tests

```bash
# Maven
./mvnw test -Dparamixel.skipTests=true

# Gradle
./gradlew check -PparamixelSkipTests
```

## The `__ParamixelRunner__` convention

A `__ParamixelRunner__` class is a console and IDE entry point that runs all `@Paramixel.Factory` methods in its package and sub-packages. The double-underscore prefix sorts the file to the top of the package in IDE file trees.

```java
package com.example.tests;

import org.paramixel.api.Runner;
import org.paramixel.api.selector.Selector;

public class __ParamixelRunner__ {
    public static void main(String[] args) {
        Runner.defaultRunner().runAndExit(
            Selector.packageTreeOf(__ParamixelRunner__.class));
    }
}
```

`Selector.packageTreeOf(Class)` discovers all `@Paramixel.Factory` methods in the specified package and all sub-packages. Running `com.example.tests.__ParamixelRunner__` from an IDE executes all test classes in `com.example.tests` and its sub-packages.

When you don't need a package runner, use `Selector` filtering via configuration keys (`paramixel.match.package.regex`, `paramixel.match.class.regex`, `paramixel.match.tag.regex`) instead.

See [Examples](../guides/examples) for the convention in action.

## Next steps

- [Actions](../core-concepts/actions)
- [Configuration](../configuration/properties)
- [Maven Plugin](../integrations/maven-plugin)
- [Gradle Integration](../integrations/gradle)
