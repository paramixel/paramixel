---
title: Selector
description: Discovery criteria for filtering action factories by package, class, and tag.
---

# Selector

A `Selector` describes discovery criteria used by `Runner` when locating `@Paramixel.Factory` methods. Selectors constrain discovery by package name, fully qualified class name, tag value, or a composition of these.

All selector regular expressions use `Pattern.matcher(CharSequence).find()` semantics. For exact matches, use anchored expressions like `^smoke$`.

```java
import org.paramixel.api.selector.Selector;
```

## Factory methods

| Method | Description |
| --- | --- |
| `Selector.all()` | Matches all action factories regardless of package, class, or tag |
| `Selector.packageRegex(String)` | Matches package names by regex |
| `Selector.classRegex(String)` | Matches fully qualified class names by regex |
| `Selector.tagRegex(String)` | Matches tag values by regex |
| `Selector.packageTreeOf(Class<?>)` | Matches a package and all subpackages below it |
| `Selector.packageOf(Class<?>)` | Matches an exact package, excluding subpackages |
| `Selector.classOf(Class<?>)` | Matches the exact fully qualified class name |

## Composition

| Method | Description |
| --- | --- |
| `Selector.and(Selector...)` | Logical AND — matches when all selectors match (at least two required) |
| `Selector.and(List<Selector>)` | Logical AND from a list |
| `Selector.or(Selector...)` | Logical OR — matches when any selector matches (at least two required) |
| `Selector.or(List<Selector>)` | Logical OR from a list |
| `Selector.not(Selector)` | Logical NOT — matches when the supplied selector does not match |

Nested AND/OR selectors are automatically flattened.

## Instance methods

| Method | Description |
| --- | --- |
| `matchesPackage(String)` | Tests whether a package name matches |
| `matchesClass(String)` | Tests whether a fully qualified class name matches |
| `matchesTag(String)` | Tests whether a tag value matches |

## Selector sub-interfaces

| Interface | Package | Extends | Description |
| --- | --- | --- | --- |
| `RegexSelector` | `org.paramixel.api.selector` | `Selector` | Base for regex-based selectors; adds `pattern()` |
| `PackageRegexSelector` | `org.paramixel.api.selector` | `RegexSelector` | Regex selector that matches package names |
| `ClassRegexSelector` | `org.paramixel.api.selector` | `RegexSelector` | Regex selector that matches class names |
| `TagRegexSelector` | `org.paramixel.api.selector` | `RegexSelector` | Regex selector that matches tag values |
| `AndSelector` | `org.paramixel.api.selector` | `Selector` | Composed AND selector; adds `selectors()` |
| `OrSelector` | `org.paramixel.api.selector` | `Selector` | Composed OR selector; adds `selectors()` |
| `NotSelector` | `org.paramixel.api.selector` | `Selector` | Negated selector; adds `selector()` |

## Examples

Select all factories in a package tree:

```java
Selector selector = Selector.packageTreeOf(MyTest.class);
runner.run(selector);
```

Select factories by tag:

```java
Selector selector = Selector.tagRegex("^smoke$");
runner.run(selector);
```

Compose criteria:

```java
Selector selector = Selector.and(
    Selector.packageTreeOf(MyTest.class),
    Selector.tagRegex("^(smoke|critical)$"));
```

Negate a selector:

```java
Selector selector = Selector.not(Selector.tagRegex("^slow$"));
```

## Use with Runner

Pass a selector to `Runner.run(Selector)`, `Runner.runAndReturnExitCode(Selector)`, or `Runner.runAndExit(Selector)`:

```java
Optional<Result> result = Runner.defaultRunner().run(
    Selector.and(
        Selector.packageTreeOf(MyTest.class),
        Selector.not(Selector.tagRegex("^slow$"))));
```

## Use with @Paramixel.Tag

Combine selectors with `@Paramixel.Tag` annotations on factory methods:

```java
@Paramixel.Factory
@Paramixel.Tag("smoke")
@Paramixel.Tag("critical")
public static Action<?> factory() {
    return Instance.of(MyTest.class)
        .child("test()", MyTest::test)
        .resolve();
}
```

Then select by tag:

```java
Selector selector = Selector.tagRegex("^smoke$");
```
