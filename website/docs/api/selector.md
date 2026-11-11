---
title: Selector
description: Which discovered factories a run selects.
---

# Selector

A `Selector` describes which discovered `@Paramixel.Factory` methods a run selects. Build one with
`Selector.builder()`.

```java
import java.util.regex.Pattern;
import org.paramixel.api.selector.Selector;

var selected = Selector.builder()
        .packageTreeOf(CheckoutTest.class)                  // X's package and its subpackages
        .tagPattern(Pattern.compile("smoke"))               // matches() against each tag
        .excludeClassPattern(Pattern.compile(".*Slow.*"))   // no pattern may match
        .build();
```

## Dimensions

| Dimension | Code-anchored | Name-based |
| --------- | ------------- | ---------- |
| Package and its subpackages | `packageTreeOf(Class)` | `packagePattern(Pattern)` |
| Class | `classOf(Class)` | `classPattern(Pattern)` |
| Tag | none; a tag is not a type | `tagPattern(Pattern)` |

Each has an `exclude…` twin: `excludePackageTreeOf`, `excludePackagePattern`, `excludeClassOf`,
`excludeClassPattern`, `excludeTagPattern`.

## Semantics

- Dimensions are ANDed. A dimension with no includes is unconstrained.
- Within a dimension any include may match, and no exclude may match.
- An empty builder selects everything.
- Patterns use `matches()` (full-match) semantics; an unescaped `.` is a wildcard. Use `Pattern.quote("...")` for a
  literal and `packageTreeOf(Class)` for exact code.
- A tag include matches a class only when at least one of its tags matches, so an untagged class is never selected
  by a tag include.
- A tag exclude rejects a class only when at least one of its tags matches, so untagged classes are kept.
  "Everything not claimed by another lane" is `excludeTagPattern(Pattern.compile("smoke|slow"))`.

Compose selectors with `and(Selector)`.

## Configured selectors

```properties
paramixel.match.package.regex=com\.example.*
paramixel.match.class.regex=.*Checkout.*
paramixel.match.tag.regex=smoke
```

`Selector.configured()` is the selector those keys describe. It is a deferred sentinel that a `Runner` resolves with
its own configuration, which lets `main` call `run(Selector.configured())` without holding a `Configuration`. It is
the only selector that is only meaningful inside a runner; `Selector.fromConfiguration(configuration)` is the
resolved form, and it throws `ConfigurationException` naming the key when a pattern is blank or invalid.
