---
title: Discovery
description: Discover action factories with Resolver.
---

# Discovery

Paramixel can discover tests by scanning for `@Paramixel.ActionFactory` methods.

## Valid action factories

A factory method must be:

- `public`
- `static`
- zero-argument
- annotated with `@Paramixel.ActionFactory`
- return an `Action`

Each class hierarchy may have **at most one** `@ActionFactory` method. If more than one is found, discovery throws `ResolverException`. To combine multiple actions, return a `Container` or `Parallel` root from a single factory:

```java
public class MyTests {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action testCreate = testCreate();
        Action testRead = testRead();

        return Container.builder("MyTests")
                .child(testCreate)
                .child(testRead)
                .build();
    }

    private static Action testCreate() {
        return Direct.builder("testCreate").runnable(ctx -> { /* ... */ }).build();
    }

    private static Action testRead() {
        return Direct.builder("testRead").runnable(ctx -> { /* ... */ }).build();
    }
}
```

## Disabled factories

Add `@Paramixel.Disabled` to exclude a factory from discovery.

## Tagging factories

Use `@Paramixel.Tag` to tag factories for selective discovery:

```java
public class SmokeTests {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    @Paramixel.Tag("smoke")
    public static Action actionFactory() {
        Action testLogin = testLogin();
        Action testDashboard = testDashboard();

        return Container.builder("SmokeTests")
                .child(testLogin)
                .child(testDashboard)
                .build();
    }

    private static Action testLogin() {
        return Direct.builder("testLogin").runnable(ctx -> { /* ... */ }).build();
    }

    private static Action testDashboard() {
        return Direct.builder("testDashboard").runnable(ctx -> { /* ... */ }).build();
    }
}
```

`@Paramixel.Tag` is repeatable — use `@Paramixel.Tags` to apply multiple tags:

```java
@Paramixel.ActionFactory
@Paramixel.Tag("smoke")
@Paramixel.Tag("fast")
public static Action fastSmokeTests() { /* ... */ }
```

Tag values are matched by `Selector.Builder.tagMatch()` and the `paramixel.match.tag` configuration key.

## Priority

Use `@Paramixel.Priority` on a test class to order discovered roots before `Resolver` builds the root `Parallel`:

```java
@Paramixel.Priority(10)
public class SlowDatabaseTests {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Container.builder("SlowDatabaseTests")
                .child(testCreateDatabase())
                .child(testMigrateDatabase())
                .build();
    }
}
```

Priority defaults to `0`. Higher values are ordered earlier, and negative values are allowed for classes that should be ordered after default-priority classes.

Priority affects the child order of the resolver-created root `Parallel`; it does not change completion order because `Parallel` executes concurrently. Manually built action trees keep the order defined by their builders.

When multiple classes are discovered, `Resolver` orders their returned root actions by:

1. priority descending
2. package name ascending
3. action name ascending
4. class name ascending

## Inheritance

`resolveActionsFromClass()` walks the full superclass chain (up to but not including `java.lang.Object`). Only the outermost (most-derived) method for any given signature is considered:

- If a child class overrides a parent's `@ActionFactory` method with its own `@ActionFactory` annotation, the child's version shadows the parent's.
- If a child class overrides a parent's `@ActionFactory` method **without** the `@ActionFactory` annotation, the parent's factory is no longer discovered for that class.
- If a child class declares its own `@ActionFactory` method (different name) and also inherits one from a parent, discovery throws `ResolverException`.

## Invalid factories

Invalid factories are not silently ignored. Discovery throws `ResolverException`. This includes:

- Methods that are not `public static`
- Methods with parameters
- Methods with a non-`Action` return type
- Methods that return `null`
- More than one `@ActionFactory` method in a class hierarchy

## Factory method execution

Discovery is not purely a scanning operation. When `Resolver.resolveActions` or `Resolver.resolveActionsFromClass` finds an `@ActionFactory` method, it **invokes the method via reflection** to produce the action instance. This means:

- Factory method code executes during the `resolveActions` call, not later
- Side effects in factory methods (e.g., resource allocation, logging) occur at discovery time
- Exceptions thrown by factory methods are wrapped in `ResolverException`

## Resolver entry points

`Resolver` has four overloads:

```java
// Resolve all actions from classpath with defaults
Optional<Action> action = Resolver.resolveActions();

// Resolve with selector
Optional<Action> action = Resolver.resolveActions(Selector selector);

// Resolve with configuration
Optional<Action> action = Resolver.resolveActions(Map<String, String> configuration);

// Resolve with configuration and selector
Optional<Action> action = Resolver.resolveActions(Map<String, String> configuration, Selector selector);
```

The old overloads accepting `ClassLoader`, `Predicate`, or `Resolver.Composition` have been removed. Use `Selector.builder()` for filtering.

## Selector

Use `Selector.builder()` to control which factories are discovered:

```java
// Match by package name (exact package + subpackages)
Selector selector = Selector.builder()
        .packageOf(MyTest.class)
        .build();

// Match by package name regex
Selector selector = Selector.builder()
        .packageMatch("com\\.example(\\..*)?")
        .build();

// Match by class name (exact)
Selector selector = Selector.builder()
        .classOf(MyTest.class)
        .build();

// Match by class name regex
Selector selector = Selector.builder()
        .classMatch("com\\.example\\..*Test")
        .build();

// Match by tag
Selector selector = Selector.builder()
        .packageOf(MyTest.class)
        .tagMatch("smoke")
        .build();
```

### Selector builder methods

| Method | Description |
|---|---|
| `packageMatch(String regex)` | Regex match against package names |
| `packageOf(Class<?> clazz)` | Exact match of the class's package + subpackages |
| `classMatch(String regex)` | Regex match against fully qualified class names |
| `classOf(Class<?> clazz)` | Exact match of fully qualified class name |
| `tagMatch(String regex)` | Regex match against `@Paramixel.Tag` values |

Only one location criterion is allowed (package or class, not both). Tag filter is optional and orthogonal.

All regex patterns use `Pattern.matcher().find()` (substring match). For exact match, anchor with `^...$`.

## Configuration-based filtering

`Resolver` also supports filtering via configuration keys:

| Key | Description |
|---|---|
| `paramixel.match.package` | Regex filter on package names |
| `paramixel.match.class` | Regex filter on fully qualified class names |
| `paramixel.match.tag` | Regex filter on `@Paramixel.Tag` values |

Configuration-based patterns are applied independently from selector-based patterns — both must match for an action to be included.

```java
Optional<Action> action = Resolver.resolveActions(
        Map.of("paramixel.match.tag", "smoke"));
```

## Default behavior

`Resolver.resolveActions()` combines discovered roots as a `Parallel` root using `paramixel.parallelism` from configuration.

## Running discovered actions

Programmatically:

```java
Selector selector = Selector.builder()
        .packageOf(MyTest.class)
        .build();

Action root = Resolver.resolveActions(selector).orElseThrow();
Result result = Runner.builder().build().run(root);
```

With Maven plugin, discovered factories run automatically during the `test` phase. With Gradle, `Runner.main()` discovers and executes factories from the test classpath.
