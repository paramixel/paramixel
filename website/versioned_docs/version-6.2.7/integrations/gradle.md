---
title: Gradle
description: Run Paramixel from Gradle with a JavaExec task.
---

# Gradle

Run Paramixel from Gradle with a `JavaExec` task that invokes `org.paramixel.api.Runner` on a classpath containing Paramixel core and the classes with `@Paramixel.Factory` methods.

```groovy
tasks.register('paramixelTest', JavaExec) {
    group = 'verification'
    description = 'Discovers and executes Paramixel action trees'
    mainClass = 'org.paramixel.api.Runner'
    classpath = sourceSets.test.runtimeClasspath
}

tasks.named('check') {
    dependsOn 'paramixelTest'
}
```

Pass configuration as JVM system properties:

```groovy
tasks.named('paramixelTest') {
    systemProperty 'paramixel.match.tag.regex', 'smoke'
    systemProperty 'paramixel.report.file', "$buildDir/reports/paramixel.json"
}
```

Ensure the task classpath contains Paramixel core and the classes with `@Paramixel.Factory` methods.
