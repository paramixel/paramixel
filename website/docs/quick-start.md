---
title: Quick Start
description: Get a Paramixel test running quickly.
---

# Quick Start

## Requirements

- Java 17+

If you want to run Paramixel through the Maven plugin, you also need:

- Maven 3.9+

## Add dependencies

Add the Paramixel core dependency:

```xml
<properties>
    <paramixel.version>YOUR_PARAMIXEL_VERSION</paramixel.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.paramixel</groupId>
        <artifactId>core</artifactId>
        <version>${paramixel.version}</version>
    </dependency>
</dependencies>
```

If you want to run Paramixel tests with Maven, also add the Paramixel Maven plugin:

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

## Write a test factory

```java
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Sequential;

public class ExampleTest {

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Sequential.of(
                "ExampleTest",
                Direct.of("first", context -> {}),
                Direct.of("second", context -> {}));
    }
}
```

## Run with Maven

If you added the Paramixel Maven plugin, run:

```bash
./mvnw test
```

The plugin discovers `@Paramixel.ActionFactory` methods on the test classpath and executes the returned actions.

## Run directly from `main`

```java
import org.paramixel.core.ConsoleRunner;

public static void main(String[] args) {
    ConsoleRunner.runAndExit(actionFactory());
}
```

## Check the result programmatically

```java
Action action = ExampleTest.actionFactory();
Runner.builder().build().run(action);

if (action.getResult().getStatus().isPass()) {
    System.out.println("passed");
}
```

`Runner.run(action)` does not return a `Result`.
