---
title: Selector
description: Package, class, and tag selection for discovery.
---

# Selector

```java
Selector selector = Selector.builder()
        .packageTreeOf(MyTests.class)
        .tagPattern(Pattern.compile("^smoke$"))
        .build();

Result result = runner.run(selector);
```

| Builder method | Effect |
| --- | --- |
| `packageTreeOf(Class)` | Include a package and its subpackages |
| `packagePattern(Pattern)` / `excludePackagePattern` | Match package names |
| `classOf(Class)` / `classPattern` / `excludeClassPattern` | Match classes |
| `tagPattern` / `excludeTagPattern` | Match `@Paramixel.Tag` values |
| `Selector.configured()` | Use `paramixel.match.*` keys |
| `selector.and(other)` | Intersect selections |

## Rules

- `Runner.run(Selector)` uses exactly the supplied selector. If configured `paramixel.match.*` keys are ignored by that
  selector, the runner warns rather than merging them silently.
- `Runner.run()` uses the configured selection.
- A selection that matches nothing reports `SKIPPED`; `paramixel.failIfNoTests` (default `true`) decides the exit code.
- Selection filters factories only. `@BeforeAll` and `@AfterAll` hooks wrap the whole run and are always discovered.
