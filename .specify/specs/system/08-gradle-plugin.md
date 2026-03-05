# Paramixel -- Gradle Plugin (EXPERIMENTAL)

## Overview

The Gradle plugin (`paramixel-gradle-plugin`) provides experimental Gradle build
integration for the Paramixel engine. It provides a `paramixelTest` task that
discovers `@Paramixel.TestClass` classes in the test output directory and executes
them via the JUnit Platform Launcher.

> **EXPERIMENTAL**: This plugin is currently experimental and may undergo
> significant changes in future releases. The API, configuration options, and
> behavior are not yet stabilized.

## Plugin Configuration

**Plugin ID:** `org.paramixel`
**Task Name:** `paramixelTest`
**Default:** Part of `check` lifecycle

### Extension DSL

| Property | Type | Default | Description |
|---|---|---|---|
| `skipTests` | `Property<Boolean>` | `false` | Skips all test execution when true |
| `failIfNoTests` | `Property<Boolean>` | `true` | Fails build if no `@Paramixel.TestClass` found |
| `parallelism` | `Property<Integer>` | (engine default) | Global max parallelism; when unset, engine default applies |
| `includeTags` | `Property<String>` | (none) | Comma-separated regex patterns; includes matching tags |
| `excludeTags` | `Property<String>` | (none) | Comma-separated regex patterns; excludes matching tags |

### Execution Behavior

1. If `skipTests=true`: log "Tests are skipped." and return.
2. Build `URLClassLoader` from: test-classes dirs, runtime classpath.
3. Scan test-classes dirs recursively for `.class` files (excluding inner classes via `$`).
4. Load each class; keep those annotated `@Paramixel.TestClass`.
5. If no classes found and `failIfNoTests=true`: throw `GradleException`.
   If no classes found and `failIfNoTests=false`: log warning and return.
6. Create `LauncherDiscoveryRequest` with one `ClassSelector` per test class, filtered to
   engine `"paramixel"`, with `configurationParameter("invokedBy", "gradle")`.
7. Execute via `Launcher.execute()`.
8. If `summary.getTotalFailureCount() > 0`: throw `GradleException("Tests failed: N of M tests")`.

In Gradle invocation mode (`invokedBy=gradle`), the engine MUST print a final line containing
either `TESTS PASSED` or `TESTS FAILED`.

## Error Handling

| Condition | Result |
|---|---|
| `skipTests=true` | No exception; logs info message |
| No `@Paramixel.TestClass` found + `failIfNoTests=true` | `GradleException("No @Paramixel.TestClass annotated classes found")` |
| No `@Paramixel.TestClass` found + `failIfNoTests=false` | Warning logged; no exception |
| Classpath construction fails | `GradleException("Failed to execute Paramixel tests", cause)` |
| Test failures (N > 0) | `GradleException("Tests failed: N of M tests")` |

---

## Tag-Based Test Filtering

The engine supports filtering test classes based on `@Paramixel.Tags` annotations using
regular expressions.

### Configuration Parameters

| Parameter | Description |
|---|---|
| `paramixel.tags.include` | Comma-separated regex patterns; classes matching ANY pattern are included |
| `paramixel.tags.exclude` | Comma-separated regex patterns; classes matching ANY pattern are excluded |

**Configuration sources (same precedence as other properties):**
- System properties: `-Dparamixel.tags.include=pattern`
- Gradle CLI: `-Pparamixel.tags.include=pattern`
- JUnit Platform configuration parameters
- Properties file (`paramixel.properties`): `paramixel.tags.include=pattern`

### Matching Behavior

1. **Include patterns applied first:** A class matches if ANY of its tags matches ANY include pattern.
2. **Exclude patterns applied second:** Matching classes are removed if ANY of their tags matches ANY exclude pattern.
3. **Default behavior:** Without include patterns, all classes pass (except excluded ones).
4. **Untagged classes:** Only included when no include patterns are configured.
5. **Case sensitive:** Regex matching uses Java's default case-sensitive behavior.

### Tag Filtering Examples

```bash
# Include only integration tests
./gradlew paramixelTest -Pparamixel.tags.include="integration-.*"

# Exclude slow tests
./gradlew paramixelTest -Pparamixel.tags.exclude=".*slow.*"

# Include integration tests except slow ones
./gradlew paramixelTest -Pparamixel.tags.include="integration-.*" -Pparamixel.tags.exclude=".*slow.*"

# Include multiple patterns
./gradlew paramixelTest -Pparamixel.tags.include="^unit$,^fast$"
```

### Gradle Plugin Tag Configuration

**build.gradle:**
```groovy
paramixel {
    includeTags = 'integration-.*'
    excludeTags = '.*-slow'
}
```

**build.gradle.kts:**
```kotlin
paramixel {
    includeTags.set("integration-.*")
    excludeTags.set(".*-slow")
}
```

### Properties File Tag Configuration

Create a `paramixel.properties` file in your project root:

```properties
paramixel.tags.include=integration-.*
paramixel.tags.exclude=.*slow.*,.*flaky.*
```

### Tag Filtering Error Handling

| Condition | Result |
|---|---|
| Invalid regex pattern | Exception printed; test execution fails before discovery |
| Empty pattern string | Ignored |
| No matching classes after filtering | Discovery completes with 0 test classes |

**Strict Validation:** Invalid regex patterns in `paramixel.tags.include` or
`paramixel.tags.exclude` MUST cause immediate test execution failure. The engine
MUST validate all patterns during initialization, before test discovery begins.

---

## Java Version Requirements

The Gradle plugin requires **Java 21** for building. Gradle 8.9 does not fully
support Java 25, so the build system uses Java 21 for the Gradle plugin module
even when the rest of the project builds with Java 25.

When running tests via the Gradle plugin, the Java version used at runtime
will be the version configured for the Gradle build (typically Java 21+).
