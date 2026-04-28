---
id: configuration
title: Configuration
description: All configuration options
---

# Configuration

Paramixel configuration is loaded from multiple sources with a defined precedence.

## Configuration Class

The `Configuration` class loads configuration from classpath resources and system properties:

```java
import org.paramixel.core.Configuration;

// Load from classpath paramixel.properties and system properties
Map<String, String> config = Configuration.defaultProperties();

// Classpath only
Map<String, String> classpath = Configuration.classpathProperties();

// System properties only
Map<String, String> system = Configuration.systemProperties();
```

## Configuration Precedence

Configuration is merged from multiple sources (higher priority wins):

1. **`paramixel.properties`** (classpath resource)
2. **Maven plugin `<properties>`** (POM configuration)
3. **System properties** (`-D` flags)

System properties always override other sources.

## paramixel.properties

Create `src/test/resources/paramixel.properties`:

```properties
# Runner parallelism
paramixel.core.runner.parallelism=4
```

## Maven Plugin Properties

Configure in `pom.xml`:

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <configuration>
        <properties>
            <property>
                <key>paramixel.core.runner.parallelism</key>
                <value>4</value>
            </property>
        </properties>
    </configuration>
</plugin>
```

## System Properties

Set via JVM flags:

```bash
./mvnw test -Dparamixel.core.runner.parallelism=8
```

## Configuration Keys

### Runner Parallelism

**Key:** `paramixel.core.runner.parallelism`

**Default:** `Runtime.getRuntime().availableProcessors()`

**Description:** Default parallelism level for `Parallel` actions. Individual `Parallel` actions can override this with their own `parallelism` parameter. The executor uses this value when no explicit parallelism is specified on a `Parallel` action.

**Examples:**

```properties
paramixel.core.runner.parallelism=4
```

```bash
./mvnw test -Dparamixel.core.runner.parallelism=8
```

### Skip Tests

**Key:** `paramixel.maven.skipTests`

**Default:** `false`

**Description:** Skip Paramixel test execution entirely.

**Examples:**

```properties
paramixel.maven.skipTests=true
```

```bash
./mvnw test -Dparamixel.maven.skipTests=true
```

### Fail If No Tests

**Key:** `paramixel.maven.failIfNoTests`

**Default:** `true`

**Description:** Fail the build if no `@Paramixel.ActionFactory` methods are discovered.

**Examples:**

```properties
paramixel.maven.failIfNoTests=false
```

```bash
./mvnw test -Dparamixel.maven.failIfNoTests=false
```

## Custom Properties

Pass custom properties via system properties or Maven plugin configuration:

```xml
<configuration>
    <properties>
        <property>
            <key>my.custom.property</key>
            <value>value</value>
        </property>
    </properties>
</configuration>
```

Access custom properties:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    String customValue = System.getProperty("my.custom.property");
    ...
}
```

## Complete Example

`src/test/resources/paramixel.properties`:

```properties
# Runner configuration
paramixel.core.runner.parallelism=4

paramixel.maven.failIfNoTests=false
```

`pom.xml`:

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <configuration>
        <properties>
            <property>
                <key>paramixel.core.runner.parallelism</key>
                <value>8</value>
            </property>
        </properties>
    </configuration>
</plugin>
```

CLI:

```bash
./mvnw test -Dparamixel.core.runner.parallelism=16
```

**Result:**
- `paramixel.core.runner.parallelism` = `16` (system property wins)
- `paramixel.maven.failIfNoTests` = `false` (from file)

## Programmatic Configuration

Pass configuration programmatically to `Runner`:

```java
Map<String, String> config = Map.of(
    "paramixel.core.runner.parallelism", "8"
);

Runner executor = Runner.builder()
    .configuration(config)
    .build();
```

## See Also

- [Parallel](actions/parallel) - Configuring parallelism
- [Maven Plugin](usage/maven-plugin) - Plugin-specific configuration
