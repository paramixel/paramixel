---
title: Selector
description: Discovery criteria for package, class, and tag filtering.
---

# Selector

Selectors filter classpath-discovered factories.

## Factory methods

Use package, class, and tag regex selectors, plus boolean composition:

```java
import org.paramixel.api.selector.Selector;

var smoke = Selector.tagRegex("smoke");
var checkout = Selector.classRegex("Checkout");
var selected = Selector.and(smoke, checkout);
```

Regex selectors use Java regex `matches()` semantics. The regex must match the entire candidate string. To match substrings, use wildcards such as `.*Checkout.*`.

## Configuration equivalents

```properties
paramixel.match.package.regex=com\.example.*
paramixel.match.class.regex=.*Checkout.*
paramixel.match.tag.regex=smoke
```

Use `Runner.run(selector)` or `Runner.runAndReturnExitCode(selector)` for programmatic discovery runs.
