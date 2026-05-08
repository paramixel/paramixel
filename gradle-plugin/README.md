# Paramixel Gradle Plugin

Gradle plugin that discovers and executes Paramixel action trees via the `paramixelTest` task.

Applying the plugin adds the `paramixel` DSL extension and registers a `paramixelTest` task that is wired into the `check` lifecycle.

## Usage

Apply the plugin and configure the `paramixel` extension:

```groovy
plugins {
    id 'org.paramixel' version 'LATEST_VERSION'
}

paramixel {
    failIfNoTests = false
    parallelism = 4
}
```

Run tests:

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

## Building from Source

### Prerequisites

- JDK 17+ installed on your machine
- `JAVA_17_HOME` environment variable pointing to your JDK 17 installation directory

### Build the Maven project and Gradle plugin

From the project root:

```bash
JAVA_17_HOME=/path/to/jdk17 ./build.sh
```

This builds the Maven project with the current Java runtime, then builds the Gradle plugin using the JDK at `JAVA_17_HOME`.

### Build only the Gradle plugin

From the project root:

```bash
JAVA_17_HOME=/path/to/jdk17 ./build.sh gradle-plugin
```

The root build script will:

1. **Sync the version** from the parent POM to `gradle-plugin/gradle.properties`, ensuring the Gradle plugin version matches the core version
2. **Install Maven prerequisites** required by the Gradle plugin to the local Maven repository
3. **Run `gradle-plugin/gradlew clean build`** using the JDK at `JAVA_17_HOME`

If `JAVA_17_HOME` is not set, the script exits with an error message.

### Dependency Resolution

The Gradle plugin resolves `org.paramixel:core` from **Maven Central first**, then **Maven Local**. For local development, `./build.sh gradle-plugin` populates Maven Local with the required local artifacts. Published releases resolve directly from Maven Central without a local install.

### Version Sync

`build.sh` syncs the project version from the parent POM to `gradle.properties` before every build. This keeps the Gradle plugin version aligned with the core module version.

The `sync-gradle-version` target syncs the version without building — this is used by the release workflow during the automated release process:

```bash
./build.sh sync-gradle-version
```

---

Copyright 2026-present Douglas Hoard
