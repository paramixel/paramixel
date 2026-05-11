# Paramixel Gradle Plugin

Gradle plugin that discovers and executes Paramixel action trees via the `paramixelTest` task.

Applying the plugin adds the `paramixel` DSL extension and registers a `paramixelTest` task that is wired into the `check` lifecycle.

## Usage

Apply the plugin and configure the `paramixel` extension:

```groovy
plugins {
    id 'org.paramixel' version '<PARAMIXEL_VERSION>'
}
```

### Run Tests

```bash
./gradlew paramixelTest
```

Or as part of the full verification lifecycle:

```bash
./gradlew check
```

## DSL Properties

| Property | Type | Default | Description |
|---|---|---|---|
| `skipTests` | `Boolean` | `false` | Skip Paramixel test execution entirely |
| `failIfNoTests` | `Boolean` | `true` | Fail the build when no action factories are discovered |
| `failureOnSkip` | `Boolean` | `false` | Treat skipped results as failures |
| `parallelism` | `Integer` | *unset* | Runner parallelism level; falls back to the framework default when absent |
| `matchPackage` | `String` | *unset* | Regex filter for package names; all packages are included when absent |
| `matchClass` | `String` | *unset* | Regex filter for fully-qualified class names; all classes are included when absent |
| `matchTag` | `String` | *unset* | Regex filter for tags; all tags are included when absent |

## System Properties

System properties starting with `paramixel.` always override DSL configuration and classpath defaults, matching the precedence of the Maven plugin.

```bash
./gradlew paramixelTest -Dparamixel.skipTests=true
./gradlew paramixelTest -Dparamixel.parallelism=8
```

## Build from Source

### Prerequisites

- **JAVA_17_HOME** environment variable pointing to a JDK 17 installation directory
- **Gradle 8.x.x+**
- **Core module** built and installed to the local Maven repository

### Build

From the `gradle-plugin/` directory:

```bash
JAVA_HOME="$JAVA_17_HOME" ./gradlew clean build
```

### Dependency Resolution

The Gradle plugin resolves `org.paramixel:core` from **Maven Central first**, then **Maven Local**. For local development, build and install the core module to Maven Local from the project root:

```bash
./mvnw clean install -DskipTests -Dparamixel.skipTests
```

Published releases resolve directly from Maven Central without a local install.

### Version Sync

The plugin version in `gradle.properties` must match the core module version. Update it manually before building.

---

Copyright 2026-present Douglas Hoard
