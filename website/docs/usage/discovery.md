---
id: discovery
title: Action Discovery
description: How Paramixel discovers test actions
---

# Action Discovery

Paramixel discovers test actions by scanning the classpath for `@Paramixel.ActionFactory` methods.

## @Paramixel.ActionFactory

Mark a public static method that returns an `Action` tree:

```java
import org.paramixel.core.Paramixel;
import org.paramixel.core.Action;

@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Direct.of("test", context -> {
        })));
}
```

### Method Requirements

- **Public**: Must be `public static`
- **No arguments**: Takes no parameters
- **Returns Action**: Must return `Action` (or a subtype)

Invalid methods are skipped during discovery.

## @Paramixel.Disabled

Exclude a factory from discovery:

```java
@Paramixel.Disabled("Disabled for debugging")
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest", ...);
}
```

The disabled reason is optional but recommended for documentation.

## Resolver

`Resolver` provides programmatic action discovery via ClassGraph scanning.

### Package Scanning

```java
import org.paramixel.core.Resolver;
import org.paramixel.core.Selector;

// Discover in specific package
Optional<Action> action = Resolver.resolveActions(
    Selector.byPackageName("com.example.tests"));

// Discover in package and subpackages
Optional<Action> action = Resolver.resolveActions(
    Selector.byPackageName(MyTest.class));

// Discover in specific class
Optional<Action> action = Resolver.resolveActions(
    Selector.byClassName("com.example.tests.MyTest"));

// Discover all packages
Optional<Action> action = Resolver.resolveActions();
```

### Custom Filtering

```java
Predicate<String> packageFilter = pkg ->
    pkg.equals("com.example.tests") || pkg.startsWith("com.example.tests.");

Optional<Action> action = Resolver.resolveActions(packageFilter);
```

### Composition Strategy

Control how multiple discovered actions are composed:

```java
// Compose sequentially (default for single-class discovery)
Optional<Action> action = Resolver.resolveActions(
    Selector.byClassName(MyTest.class),
    Resolver.Composition.SEQUENTIAL);

// Compose in parallel (default for multi-package discovery)
Optional<Action> action = Resolver.resolveActions(
    Selector.byPackageName("com.example.tests"),
    Resolver.Composition.PARALLEL);
```

### ClassLoader Control

Use a custom classloader for discovery:

```java
ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

Optional<Action> action = Resolver.resolveActions(
    classLoader,
    Selector.byPackageName("com.example.tests"),
    Resolver.Composition.PARALLEL);
```

### Single-Class Discovery

Discover factories from a specific class:

```java
Optional<Action> action = Resolver.resolveActionsFromClass(MyTest.class);
```

Always uses `SEQUENTIAL` composition.

## Programmatic Execution

Execute discovered actions programmatically:

```java
public static void main(String[] args) {
    Optional<Action> optionalAction = Resolver.resolveActions(
        Selector.byPackageName(MyTest.class));

    if (optionalAction.isEmpty()) {
        System.out.println("No tests found");
        return;
    }

    Result result = Runner.builder()
        .build()
        .run(optionalAction.get());

    int exitCode = result.status() == Result.Status.PASS ? 0 :1;
    System.exit(exitCode);
}
```

## Maven Plugin Discovery

The Maven plugin automatically discovers `@Paramixel.ActionFactory` methods on the test classpath:

1. Scans all classes on the test classpath
2. Finds methods with `@Paramixel.ActionFactory` annotation
3. Skips methods with `@Paramixel.Disabled`
4. Validates method signatures (public, static, no args, returns Action)
5. Invokes each factory method to build the action tree
6. Composes all discovered actions into a `Parallel` root action

### Test Classpath

The plugin discovers from:
- `target/test-classes` (compiled test code in `src/main/java` or `src/test/java`)
- All test-scope dependencies

## Discovery Rules

### Inheritance

Subclasses do **not** inherit `@Paramixel.ActionFactory` from superclasses. Each class must declare its own factory.

### Method Visibility

Only `public static` methods are discovered. Package-private or protected methods are ignored.

### Return Type

The method must return `Action` (or a subtype). Methods returning `void`, `Object`, or other types are ignored.

### Exceptions During Discovery

If a factory method throws an exception during invocation:
- The exception is logged
- That factory is skipped
- Other factories continue to be discovered

If no valid factories are found:
- If `failIfNoTests` is `true` (default), the build fails
- If `failIfNoTests` is `false`, a warning is logged

## Selector

`Selector` describes a classpath selection as a regular expression.

### By Package Name

```java
Selector.byPackageName("com.example.tests");
// Matches: com.example.tests and all subpackages
```

```java
Selector.byPackageName(MyTest.class);
// Matches: MyTest's package and all subpackages
```

### By Class Name

```java
Selector.byClassName("com.example.tests.MyTest");
// Matches: exactly this class
```

```java
Selector.byClassName(MyTest.class);
// Matches: exactly this class
```

## Example: Console Package Runner

Paramixel provides a package-level runner class for headless execution:

```java
package com.example.tests;

import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Selector;

public class ConsolePackageRunner {

    public static void main(String[] args) {
        ConsoleRunner.runAndExit(selector());
    }

    private static Selector selector() {
        return Selector.byPackageName(ConsolePackageRunner.class);
    }
}
```

The runner uses `Selector.byPackageName()` to scan its own package and all subpackages and runs headlessly.

## Example: Custom Runner Configuration

```java
public static void main(String[] args) {
    Optional<Action> optionalAction = Resolver.resolveActions(
        Selector.byPackageName(MyTest.class));

    optionalAction.ifPresent(action -> {
        Map<String, String> config = Map.of(
            "paramixel.core.runner.parallelism", "8"
        );

        Result result = Runner.builder()
            .configuration(config)
            .build()
            .run(action);

        int exitCode = result.status() == Result.Status.PASS ? 0 :1;
        System.exit(exitCode);
    });
}
```

## See Also

- [Action Composition](action-composition) - Building action trees
- [Maven Plugin](maven-plugin) - Plugin discovery and execution
- [Configuration](../configuration) - Runner configuration
