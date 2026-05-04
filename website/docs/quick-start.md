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
    <paramixel.version>2.0.0</paramixel.version>
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

See [Maven Central](https://central.sonatype.com/search?namespace=org.paramixel) for the latest published version.

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

To also write a per-run summary file:

```bash
./mvnw test -Dparamixel.report.enabled=true
```

By default the file is written under `target/paramixel/` with a name like `paramixel_20260504-123456.log`.

## Run directly from `main`

```java
import org.paramixel.core.Runner;

public static void main(String[] args) {
    Runner runner = Runner.builder().build();
    Result result = runner.run(actionFactory());
    System.exit(runner.runAndReturnExitCode(actionFactory()));
}
```

## Check the result programmatically

```java
Result result = Factory.defaultRunner().run(action);

if (result.getStatus().isPass()) {
    System.out.println("passed");
}
```

`Runner.run(Action)` returns a `Result` that you can inspect after execution.

A `Runner` can execute multiple actions. Each `run()` call is independent:

```java
Runner runner = Runner.builder().build();
Result first  = runner.run(firstAction);
Result second = runner.run(secondAction);
// Owned executors are created and shut down per run
```

When you provide your own `ExecutorService`, the runner uses it but does not manage its lifecycle:

```java
ExecutorService myPool = Executors.newFixedThreadPool(4);
Runner runner = Runner.builder().executorService(myPool).build();
Result result = runner.run(action);
myPool.shutdown(); // you are responsible for shutting down
```
