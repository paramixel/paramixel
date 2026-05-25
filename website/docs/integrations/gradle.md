---
title: Gradle
description: Run Paramixel tests with Gradle.
---

# Gradle

Paramixel does not have a dedicated Gradle plugin. Instead, use a `JavaExec` task that runs `Runner.main()` to discover and execute action trees from the classpath.

## Basic setup

### `build.gradle`

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

### `settings.gradle`

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'my-project'
```

## Project structure

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

## Configuration

### System properties

Pass Paramixel configuration as JVM system properties:

```groovy
tasks.register('paramixelTest', JavaExec) {
    dependsOn 'paramixelClasses'
    group = 'verification'
    description = 'Discovers and executes Paramixel action trees'
    mainClass = 'org.paramixel.api.Runner'
    classpath = sourceSets.paramixel.runtimeClasspath
    systemProperty('paramixel.parallelism', '4')
    systemProperty('paramixel.match.tag.regex', 'smoke')
    systemProperty('paramixel.report.file', layout.buildDirectory.file('paramixel/report.json').get().asFile.absolutePath)
}
```

### CLI

```bash
./gradlew paramixelTest -Dparamixel.parallelism=8
./gradlew paramixelTest -Dparamixel.match.tag.regex=smoke
./gradlew paramixelTest -Dparamixel.report.file=build/paramixel/report.json
```

### Properties file

Place `paramixel.properties` in `src/paramixel/resources/`:

```properties
paramixel.parallelism=4
paramixel.report.file=build/paramixel/report.json
```

### Configuration precedence

| Priority | Source |
| --- | --- |
| 1 (lowest) | Built-in defaults |
| 2 | Classpath `paramixel.properties` |
| 3 (highest) | JVM system properties (`-Dparamixel.*` or `systemProperty(...)`) |

## Skipping Paramixel tests

```groovy
tasks.named('check').configure {
    if (!project.hasProperty('paramixelSkipTests')) {
        dependsOn 'paramixelTest'
    }
}
```

```bash
./gradlew check -PparamixelSkipTests
```

## Adding test dependencies

Add dependencies to the `paramixelImplementation` configuration:

```groovy
dependencies {
    paramixelImplementation 'org.paramixel:core:5.0.0'
    paramixelImplementation 'org.assertj:assertj-core:3.27.3'
    paramixelImplementation 'org.testcontainers:testcontainers:1.20.0'
    paramixelImplementation 'org.testcontainers:nginx:1.20.0'
}
```

## Running alongside JUnit tests

The `paramixelTest` task is independent of the `test` task. To run both:

```bash
./gradlew check
```

To run only Paramixel tests:

```bash
./gradlew paramixelTest
```

To run only JUnit tests:

```bash
./gradlew test
```

## Integration testing with Testcontainers

```groovy
dependencies {
    paramixelImplementation 'org.paramixel:core:5.0.0'
    paramixelImplementation 'org.testcontainers:testcontainers:1.20.0'
    paramixelImplementation 'org.testcontainers:kafka:1.20.0'
    paramixelImplementation 'org.testcontainers:nginx:1.20.0'
    paramixelImplementation 'org.testcontainers:mongodb:1.20.0'
    paramixelImplementation 'org.apache.kafka:kafka-clients:3.9.0'
    paramixelImplementation 'org.mongodb:mongodb-driver-sync:5.2.0'
}
```

## Kotlin DSL

The same setup using the Kotlin DSL:

### `build.gradle.kts`

```kotlin
plugins {
    id("java")
}

sourceSets {
    create("paramixel") {
        java {
            srcDir("src/paramixel/java")
        }
        resources {
            srcDir("src/paramixel/resources")
        }
    }
}

dependencies {
    "paramixelImplementation"("org.paramixel:core:5.0.0")
}

tasks.register<JavaExec>("paramixelTest") {
    dependsOn("paramixelClasses")
    group = "verification"
    description = "Discovers and executes Paramixel action trees"
    mainClass = "org.paramixel.api.Runner"
    classpath = sourceSets["paramixel"].runtimeClasspath
}

tasks.named("check").configure {
    dependsOn("paramixelTest")
}
```

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "my-project"
```

### Kotlin DSL configuration

```kotlin
tasks.register<JavaExec>("paramixelTest") {
    dependsOn("paramixelClasses")
    group = "verification"
    description = "Discovers and executes Paramixel action trees"
    mainClass = "org.paramixel.api.Runner"
    classpath = sourceSets["paramixel"].runtimeClasspath
    systemProperty("paramixel.parallelism", "4")
    systemProperty("paramixel.match.tag.regex", "smoke")
    systemProperty("paramixel.report.file",
        layout.buildDirectory.file("paramixel/report.json").get().asFile.absolutePath)
}
```

## Next steps

- [Maven Plugin](./maven-plugin)
- [Reporting](./reporting)
- [Configuration Properties](../configuration/properties)
