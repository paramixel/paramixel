---
title: Profiles
description: Using configuration profiles with Paramixel.
---

# Profiles

Paramixel does not have a built-in profile system. Use standard Java and build tool mechanisms to achieve profile-like behavior.

## Approach 1: System property overrides

```bash
# CI profile
./mvnw test -Dparamixel.parallelism=8 -Dparamixel.report.file=target/paramixel/report.json

# Local profile
./mvnw test -Dparamixel.parallelism=2

# Smoke tests only
./mvnw test -Dparamixel.match.tag.regex=smoke
```

## Approach 2: Multiple properties files

Use Maven or Gradle resource filtering to place the desired `paramixel.properties` on the runtime classpath.

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <filtering>true</filtering>
            <includes>
                <include>paramixel.properties</include>
            </includes>
        </resource>
    </resources>
</build>
```

## Approach 3: Programmatic configuration

Build a `Runner` with an explicit `Configuration`:

```java
Map<String, String> config = new HashMap<>();

if (isCiEnvironment()) {
    config.put(Configuration.RUNNER_PARALLELISM, "8");
    config.put(Configuration.REPORT_FILE, "target/paramixel/report.json");
} else {
    config.put(Configuration.RUNNER_PARALLELISM, "2");
}

Runner runner = Runner.builder()
        .configuration(Configuration.of(config))
        .build();
```

## Approach 4: Tag-based selection

Use `@Paramixel.Tag` to categorize tests and filter with `paramixel.match.tag.regex`:

```java
@Paramixel.Factory
@Paramixel.Tag("smoke")
public static Action<?> smokeTests() { /* ... */ }

@Paramixel.Factory
@Paramixel.Tag("integration")
public static Action<?> integrationTests() { /* ... */ }
```

```bash
./mvnw test -Dparamixel.match.tag.regex=smoke
./mvnw test -Dparamixel.match.tag.regex=integration
```

## Next steps

- [Configuration Properties](./properties)
- [System Properties](./system-properties)
