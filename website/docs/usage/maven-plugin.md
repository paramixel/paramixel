---
id: maven-plugin
title: Maven Plugin
description: Maven plugin configuration and usage
---

# Maven Plugin

The Paramixel Maven plugin discovers `@Paramixel.ActionFactory` methods on the test classpath and executes the returned action trees.

## Basic Configuration

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <executions>
        <execution>
            <phase>test</phase>
            <goals>
                <goal>test</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The plugin is bound to the `test` phase and executes after test compilation.

## Plugin Parameters

### skipTests

Skip Paramixel test execution:

```xml
<configuration>
    <skipTests>true</skipTests>
</configuration>
```

Or via system property:

```bash
./mvnw test -Dparamixel.maven.skipTests=true
```

### failIfNoTests

Fail the build if no `@Paramixel.ActionFactory` methods are found. Default is `true`:

```xml
<configuration>
    <failIfNoTests>false</failIfNoTests>
</configuration>
```

Or via system property:

```bash
./mvnw test -Dparamixel.maven.failIfNoTests=false
```

### properties

Pass configuration properties:

```xml
<configuration>
    <properties>
        <property>
            <key>paramixel.core.runner.parallelism</key>
            <value>4</value>
        </property>
    </properties>
</configuration>
```

## Configuration Precedence

Configuration is merged from multiple sources (higher priority wins):

1. **`paramixel.properties`** (classpath resource)
2. **Maven plugin `<properties>`** (POM configuration)
3. **System properties** (`-D` flags)

System properties always override other sources.

## Discovery

The plugin scans the test classpath for `@Paramixel.ActionFactory` methods:

1. Scan all classes on the test classpath
2. Find methods annotated with `@Paramixel.ActionFactory`
3. Skip methods also annotated with `@Paramixel.Disabled`
4. Validate: must be `public static`, no parameters, returns `Action`
5. Invoke each factory method to build the action tree
6. Compose all discovered actions into a parallel root action

All tests run in parallel by default. Use `Resolver` for programmatic discovery with different composition strategies.

## Test Class Location

Paramixel test code typically lives in `src/main/java` (not `src/test/java`) to enable shared code between tests and production classes. The plugin discovers `@Paramixel.ActionFactory` methods from the test classpath regardless of source location.

## Running Specific Tests

To run only specific tests, use package filtering or disable tests via `@Paramixel.Disabled`:

```java
@Paramixel.Disabled("Disabled for debugging")
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest", ...);
}
```

Or use a custom `Resolver` with package filtering for programmatic execution.

## CLI Examples

### Run all tests

```bash
./mvnw test
```

### Skip Paramixel tests

```bash
./mvnw test -Dparamixel.maven.skipTests=true
```

### Do not fail if no tests found

```bash
./mvnw test -Dparamixel.maven.failIfNoTests=false
```

### Set parallelism

```bash
./mvnw test -Dparamixel.core.runner.parallelism=8
```

## Build Lifecycle Integration

The plugin integrates with the Maven build lifecycle:

```
compile → test-compile → test → verify
```

Paramixel tests run in the `test` phase, after `test-compile` completes.

## Multiple Executions

Configure multiple plugin executions for different test phases:

```xml
<plugin>
    <groupId>org.paramixel</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>${paramixel.version}</version>
    <executions>
        <execution>
            <id>quick-tests</id>
            <phase>test</phase>
            <goals>
                <goal>test</goal>
            </goals>
            <configuration>
                <properties>
                    <property>
                        <key>paramixel.core.runner.parallelism</key>
                        <value>2</value>
                    </property>
                </properties>
            </configuration>
        </execution>
        <execution>
            <id>full-tests</id>
            <phase>integration-test</phase>
            <goals>
                <goal>test</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## See Also

- [Configuration](../configuration) - All configuration options
- [Discovery](discovery) - How actions are discovered
- [Architecture](../architecture) - Plugin architecture
