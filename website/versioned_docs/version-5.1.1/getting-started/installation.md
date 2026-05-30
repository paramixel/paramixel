---
title: Installation
description: Add Paramixel to your Java project.
---

# Installation

## Requirements

- Java 17 or later
- Maven 3.9+ (for the Maven plugin)

## Maven

Add the core dependency:

```xml
<properties>
    <paramixel.version>5.1.0</paramixel.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.paramixel</groupId>
        <artifactId>core</artifactId>
        <version>${paramixel.version}</version>
    </dependency>
</dependencies>
```

To run Paramixel tests during the Maven `test` phase, add the Maven plugin:

```xml
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

## Gradle

Add the core dependency:

```groovy
dependencies {
    implementation 'org.paramixel:core:5.1.0'
}
```

Register a `JavaExec` task for Paramixel test execution:

```groovy
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
    paramixelImplementation 'org.paramixel:core:5.1.0'
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

See [Gradle Integration](../integrations/gradle) for advanced configuration.

## Verify

Run the tests to confirm everything is set up:

```bash
# Maven
./mvnw test

# Gradle
./gradlew paramixelTest
```

## Next steps

- [First Test](./first-test)
