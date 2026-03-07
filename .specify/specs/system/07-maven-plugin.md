# Paramixel -- Maven Plugin

## Overview

The Maven plugin (`paramixel-maven-plugin`) bridges the Maven build lifecycle to the
Paramixel engine. It provides a single goal (`test`) that discovers `@Paramixel.TestClass`
classes in the test output directory and executes them via the JUnit Platform Launcher.

## Mojo Configuration

**Goal:** `test`
**Default phase:** `test`
**Requires:** Project present, test dependency resolution.

### Parameters

| Parameter | Property | Default | Type | Description |
|---|---|---|---|---|
| `project` | (injected) | (injected) | `MavenProject` | The current Maven project |
| `skipTests` | `paramixel.skipTests` | `false` | `boolean` | Skips all test execution when true |
| `failIfNoTests` | `paramixel.failIfNoTests` | `true` | `boolean` | Fails build if no `@Paramixel.TestClass` found |
| `parallelism` | `paramixel.parallelism` | (engine default) | `Integer` | Global max parallelism; when unset, engine default applies |
| `verbose` | `paramixel.verbose` | `false` | `boolean` | Enables verbose output (partially implemented) |
| `tagsInclude` | `paramixel.tags.include` | (none) | `String` | Comma-separated regex patterns; includes matching tags |
| `tagsExclude` | `paramixel.tags.exclude` | (none) | `String` | Comma-separated regex patterns; excludes matching tags |
| `summaryClassNameMaxLength` | `paramixel.summary.classNameMaxLength` | `2147483647` | `Integer` | Maximum rendered class-name length in the Maven-only summary table |

## Execution Behavior

1. If `skipTests=true`: log "Tests are skipped." and return.
2. Build `URLClassLoader` from: test-classes dir, classes dir, all test classpath elements.
3. Scan test-classes dir recursively for `.class` files (excluding inner classes via `$`).
4. Load each class; keep those annotated `@Paramixel.TestClass`.
5. If no classes found and `failIfNoTests=true`: throw `MojoFailureException`.
   If no classes found and `failIfNoTests=false`: log warning and return.
6. Create `LauncherDiscoveryRequest` with one `ClassSelector` per test class, filtered to
   engine `"paramixel"`, with `configurationParameter("invokedBy", "maven")`.
7. Execute via `Launcher.execute()`.
8. If `summary.getTotalFailureCount() > 0`: throw `MojoFailureException("Tests failed: N of M tests")`.

In Maven invocation mode (`invokedBy=maven`), the engine MUST print a final line containing
either `TESTS PASSED` or `TESTS FAILED`.

## Error Handling

| Condition | Result |
|---|---|
| `skipTests=true` | No exception; logs info message |
| No `@Paramixel.TestClass` found + `failIfNoTests=true` | `MojoFailureException("No @Paramixel.TestClass annotated classes found")` |
| No `@Paramixel.TestClass` found + `failIfNoTests=false` | Warning logged; no exception |
| Classpath construction fails | `MojoExecutionException("Failed to execute Paramixel tests", cause)` |
| Test failures (N > 0) | `MojoFailureException("Tests failed: N of M tests")` |

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
- Maven CLI: `-Dparamixel.tags.include=pattern`
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
./mvnw test -Dparamixel.tags.include="integration-.*"

# Exclude slow tests
./mvnw test -Dparamixel.tags.exclude=".*slow.*"

# Include integration tests except slow ones
./mvnw test -Dparamixel.tags.include="integration-.*" -Dparamixel.tags.exclude=".*slow.*"

# Include multiple patterns
./mvnw test -Dparamixel.tags.include="^unit$,^fast$"
```

### Maven Plugin Tag Configuration

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>paramixel-maven-plugin</artifactId>
    <configuration>
        <tagsInclude>integration-.*</tagsInclude>
        <tagsExclude>.*-slow</tagsExclude>
        <summaryClassNameMaxLength>60</summaryClassNameMaxLength>
    </configuration>
</plugin>
```

### Properties File Tag Configuration

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

## Maven Summary Table Class Name Rendering

When invoked by Maven (`invokedBy=maven`), Paramixel emits a `Paramixel Test Summary` table.
The class name column can be abbreviated to improve readability.

### Configuration Parameter

| Key | Description |
|---|---|
| `paramixel.summary.classNameMaxLength` | Maximum length in characters for the rendered class name in the summary table (best-effort; final segment is always preserved) |

### Abbreviation Rules

If a configured maximum length is smaller than the full class name, the engine abbreviates the
name by shortening package segments while keeping the final segment intact.

1. The final segment (after the last `.`) is always kept intact.
2. Every other segment is either the full segment or its first character.
3. Start with all non-final segments abbreviated to 1 character, then attempt to expand segments
   from right to left to their full names while the overall rendered value remains within the
   maximum. If a segment cannot be expanded without exceeding the maximum, the engine stops
   expanding and all remaining segments to the left remain abbreviated.

If the final segment alone exceeds the configured maximum, it is still kept intact and the
rendered class name may exceed the configured maximum.

Example: `foo.bar.Class`

- With `paramixel.summary.classNameMaxLength=11`: `f.bar.Class`
- With `paramixel.summary.classNameMaxLength=10`: `f.b.Class`

Example: `test.argument.ArgumentsTest`

- With `paramixel.summary.classNameMaxLength=20`: `t.a.ArgumentsTest`

This is display-only and may cause ambiguous/colliding rendered names. Increase the maximum
length when you need unambiguous output.

### Properties File Configuration

```properties
paramixel.summary.classNameMaxLength=60
```

### Error Handling

**Strict validation:** If `paramixel.summary.classNameMaxLength` is provided, it MUST be a base-10
integer in the range `[1, 2147483647]`. Invalid values MUST fail test execution immediately.
