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

Example:

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Direct.of("example", context -> {});
}
```

## Disabled factories

Add `@Paramixel.Disabled` to exclude a factory from discovery.

## Invalid factories

Invalid factories are not silently ignored. Discovery throws `ResolverException`.

## Resolver entry points

```java
Optional<Action> action = Resolver.resolveActions();
Optional<Action> action = Resolver.resolveActions(Resolver.Composition.SEQUENTIAL);
Optional<Action> action = Resolver.resolveActions("com\\.example(\\..*)?");
Optional<Action> action = Resolver.resolveActions(Selector.byPackageName("com.example.tests"));
Optional<Action> action = Resolver.resolveActionsFromClass(MyTest.class);
```

## Selector examples

```java
Selector.byPackageName("com.example.tests");
Selector.byPackageName(MyTest.class);
Selector.byClassName("com.example.tests.MyTest");
Selector.byClassName(MyTest.class);
```

## Default composition

`Resolver.resolveActions()` combines discovered roots with `Resolver.Composition.PARALLEL`.

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
