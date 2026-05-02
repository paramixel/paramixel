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

Each class hierarchy may have **at most one** `@ActionFactory` method. If more than one is found, discovery throws `ResolverException`. To combine multiple actions, return a `Sequential` or `Parallel` root from a single factory:

```java
@Paramixel.ActionFactory
public static Action allTests() {
    return Sequential.of("MyTests",
        Direct.of("testCreate", ctx -> { /* ... */ }),
        Direct.of("testRead",   ctx -> { /* ... */ })
    );
}
```

## Disabled factories

Add `@Paramixel.Disabled` to exclude a factory from discovery.

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

```java
// Resolve all actions from classpath (default: PARALLEL composition)
Optional<Action> action = Resolver.resolveActions();

// Resolve with specific composition mode
Optional<Action> action = Resolver.resolveActions(Resolver.Composition.SEQUENTIAL);

// Resolve from specific package
Optional<Action> action = Resolver.resolveActions("com\\.example(\\..*)?");

// Resolve with selector
Optional<Action> action = Resolver.resolveActions(Selector.byPackageName("com.example.tests"));

// Resolve with class loader
Optional<Action> action = Resolver.resolveActions(testClassLoader);

// Resolve from a specific class
Optional<Action> action = Resolver.resolveActionsFromClass(MyTest.class);
```

## Configuration-aware discovery

When discovered actions are combined with `PARALLEL` composition, the resulting `Parallel` root uses the `paramixel.parallelism` configuration key to control concurrency. Overloads that accept a `Map<String, String> configuration` parameter let you control parallelism at discovery time:

```java
// Resolve with explicit configuration
Optional<Action> action = Resolver.resolveActions(
    Map.of(Configuration.RUNNER_PARALLELISM, "4"));

// Resolve with selector and configuration
Optional<Action> action = Resolver.resolveActions(
    Selector.byPackageName(MyTest.class),
    Map.of(Configuration.RUNNER_PARALLELISM, "4"));

// Resolve with class loader and configuration
Optional<Action> action = Resolver.resolveActions(
    testClassLoader,
    Map.of(Configuration.RUNNER_PARALLELISM, "4"));
```

### Parallelism resolution

When `paramixel.parallelism` is provided in the configuration map, that value is used directly. Otherwise, `Configuration.defaultProperties()` is consulted (classpath file, then JVM system properties, then `Runtime.getRuntime().availableProcessors()`).

Overloads that do not accept a configuration map always use `Configuration.defaultProperties()`.

## Selector examples

```java
Selector.byPackageName("com.example.tests");
Selector.byPackageName(MyTest.class);
Selector.byClassName("com.example.tests.MyTest");
Selector.byClassName(MyTest.class);
```

## Default composition

`Resolver.resolveActions()` combines discovered roots with `Resolver.Composition.PARALLEL`. The parallel root uses `paramixel.parallelism` from configuration.

## Running discovered actions

With `ConsoleRunner`:

```java
ConsoleRunner.runAndExit(Selector.byPackageName(MyTest.class));
```

Programmatically:

```java
Action root = Resolver.resolveActions(Selector.byPackageName(MyTest.class)).orElseThrow();
Runner.builder().build().run(root);
```

Then inspect `root.getResult()`.