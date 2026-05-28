---
title: Discovery
description: How Paramixel discovers actions from the classpath.
---

# Discovery

Paramixel can discover `Action` instances from the classpath using `@Paramixel.Factory` methods. Discovery is optional — you can also construct and run actions programmatically.

## @Paramixel.Factory

Mark a `public static` no-argument method that returns `Spec<?>` or `Action<?>` with `@Paramixel.Factory`:

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

public class MyTest {

    @Paramixel.Factory
    public static Action<?> factory() {
        return Step.of("my-test", ctx -> {
            // test logic
        });
    }
}
```

Requirements for a valid factory method:

- `public` and `static`
- Zero arguments
- Annotated with `@Paramixel.Factory`
- Returns `Spec`, `Action`, or `null`
- Not annotated with `@Paramixel.Disabled`

Factories that return null are skipped.

## @Paramixel.Disabled

Exclude a factory method from discovery:

```java
@Paramixel.Factory
@Paramixel.Disabled("temporarily disabled for investigation")
public static Action<?> flakyTest() {
    // ...
}
```

## @Paramixel.Tag

Tag factory methods for selective discovery:

```java
@Paramixel.Factory
@Paramixel.Tag("smoke")
public static Action<?> smokeTest() {
    // ...
}

@Paramixel.Factory
@Paramixel.Tag("integration")
public static Action<?> integrationTest() {
    // ...
}
```

Multiple tags are supported:

```java
@Paramixel.Tag("smoke")
@Paramixel.Tag("critical")
```

`@Paramixel.Tag` is `@Repeatable(@Paramixel.Tags.class)`. The container annotation `@Paramixel.Tags` is used internally when multiple tags are declared on the same method. You do not need to use `@Paramixel.Tags` directly — simply repeat `@Paramixel.Tag`.

## @Paramixel.Priority

Control discovery ordering. Higher priority classes are ordered earlier:

```java
@Paramixel.Priority(100)
public class CriticalTests {
    @Paramixel.Factory
    public static Action<?> factory() { /* ... */ }
}
```

Priority affects scheduling admission order only; concurrent execution does not imply completion order.

## @Paramixel.Id

Assign a stable identifier to a method for `AnnotationResolver` lookup:

```java
public class MyTest {
    @Paramixel.Id("login")
    public void login() { /* ... */ }

    @Paramixel.Id("logout")
    public void logout() { /* ... */ }
}
```

See [AnnotationResolver](#annotationresolver) below.

## Selector

`Selector` is an interface that describes discovery criteria. Create selectors with static factory methods:

See [Selector API](../api/selector) for the full API details.

```java
import org.paramixel.api.selector.Selector;

// Match all factories
Selector.all();

// Match a class's package and subpackages
Selector.packageTreeOf(MyTest.class);

// Match a class's exact package (excluding subpackages)
Selector.packageOf(MyTest.class);

// Match a single class
Selector.classOf(MyTest.class);

// Regex-based selectors
Selector.packageRegex("com\\.example");
Selector.classRegex("com\\.example\\..*Test");
Selector.tagRegex("smoke");
```

All regex-based selectors use Java regex `find()` semantics. For exact match, anchor with `^...$`.

### Selector composition

Combine selectors with logical `and`, `or`, and `not`:

```java
// AND — factory must match ALL selectors
Selector.and(Selector.packageRegex("com\\.example"), Selector.tagRegex("smoke"));

// OR — factory must match ANY selector
Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("integration"));

// NOT — factory must NOT match the selector
Selector.not(Selector.packageRegex("com\\.example\\.slow"));
```

`and` and `or` accept varargs or a `List<Selector>`. Compose arbitrarily:

```java
Selector selector = Selector.and(
        Selector.packageRegex("com\\.example"),
        Selector.or(Selector.tagRegex("smoke"), Selector.tagRegex("critical")),
        Selector.not(Selector.classRegex(".*Slow"))
);
```

### Selector query methods

Every `Selector` exposes query methods to inspect its criteria:

```java
selector.matchesPackage("com.example.tests");  // true if package matches
selector.matchesClass("com.example.MyTest");    // true if class matches
selector.matchesTag("smoke");                   // true if tag matches
```

## Discovery via Runner

`Runner` discovers actions from the classpath when a `Selector` is provided:

```java
import org.paramixel.api.Runner;
import org.paramixel.api.selector.Selector;

Runner runner = Runner.defaultRunner();

// Resolve and run all discovered actions
int exitCode = runner.run();

// Resolve and run actions matching a selector
Optional<Result> result = runner.run(Selector.packageTreeOf(MyTest.class));
```

Discovery is performed by the runner's internal classpath scanner using the runner's effective configuration. Discovered actions are always combined as a `Parallel` root, ordered by priority descending, then package name, class name, and factory method name.

## AnnotationResolver

`AnnotationResolver` resolves `@Paramixel.Id` annotated methods on a concrete type into named `Action` instances:

```java
import org.paramixel.api.AnnotationResolver;

AnnotationResolver<MyTest> resolver = AnnotationResolver.create(MyTest.class);

// Instance method
Action<MyTest> login = resolver.byId("login");

// Static method
Action<?> staticAction = resolver.staticById("staticSetup");
```

Use this to compose test actions from annotated methods on a test class, especially with `Instance`:

```java
Instance.of(MyTest.class)
        .child(resolver.byId("login"))
        .child(resolver.byId("verify"))
        .child(resolver.byId("logout"))
        .resolve();
```

See [AnnotationResolver API](../api/annotation-resolver) for the full API details including caching, method signature requirements, and custom kinds.

## Configuration-driven filtering

Set these keys in `paramixel.properties` or system properties to filter discovery:

| Key | Description |
| --- | --- |
| `paramixel.match.package.regex` | Regex matched against package names |
| `paramixel.match.class.regex` | Regex matched against fully qualified class names |
| `paramixel.match.tag.regex` | Regex matched against `@Paramixel.Tag` values |

```bash
./mvnw test -Dparamixel.match.tag.regex=smoke
./gradlew paramixelTest -Dparamixel.match.tag.regex=smoke
```

## Running discovered actions

### With Runner

```java
Runner runner = Runner.defaultRunner();
runner.run();  // discovers and runs all actions
```

### With Selector

```java
Runner runner = Runner.defaultRunner();
Optional<Result> result = runner.run(Selector.packageTreeOf(MyTest.class));
```

### Exit code

```java
int exitCode = runner.runAndReturnExitCode(action);
int exitCode = runner.runAndReturnExitCode(selector);
int exitCode = runner.run();  // discovers from classpath
```

Exit code `0` indicates success; `1` indicates failure. Skipped and aborted outcomes can be configured as failures (see [Configuration](../configuration/properties)).
