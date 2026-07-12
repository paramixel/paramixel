---
title: Migration 6.2.x to 6.2.1
description: Upgrade plan for moving Paramixel 6.2.x projects to 6.2.1.
---

# Migration 6.2.x to 6.2.1

Use this plan when upgrading a Paramixel 6.2.x project to 6.2.1.

## Bug fix: regex selector matching

`Selector.packageRegex()`, `Selector.classRegex()`, `Selector.tagRegex()`, and the `paramixel.match.*.regex`
configuration keys incorrectly used `Pattern.find()` (substring match) instead of `Pattern.matches()` (full-string
match). The `match` in the configuration key names specifies the intended contract; the implementation did not
honor it. This is now fixed.

**Before (6.2.x):** The regex was matched with `find()` semantics — it could match any substring of the candidate.

**After (6.2.1):** The regex is matched with `matches()` semantics — it must match the entire candidate string.

## Pattern migration

| Old pattern (6.2.x) | Old behavior | New pattern (6.2.1) | New behavior |
| --- | --- | --- | --- |
| `smoke` | Matches `"smoke"`, `"smoke-test"`, `"nosmoke"` | `smoke` | Matches only `"smoke"` |
| `smoke` | Matches `"smoke"`, `"smoke-test"`, `"nosmoke"` | `.*smoke.*` | Matches `"smoke"`, `"smoke-test"`, `"nosmoke"` |
| `Checkout` | Matches `"CheckoutTest"`, `"MyCheckout"` | `Checkout` | Matches only `"Checkout"` |
| `Checkout` | Matches `"CheckoutTest"`, `"MyCheckout"` | `.*Checkout.*` | Matches `"CheckoutTest"`, `"MyCheckout"` |
| `com\.example` | Matches `"com.example"`, `"com.example.sub"` | `com\.example` | Matches only `"com.example"` |
| `com\.example` | Matches `"com.example"`, `"com.example.sub"` | `com\.example.*` | Matches `"com.example"`, `"com.example.sub"` |
| `^smoke$` | Matches only `"smoke"` | `^smoke$` | Matches only `"smoke"` (no change) |

## Migration steps

### 1. Update `paramixel.properties`

```properties
# Before (6.2.x)
paramixel.match.tag.regex=smoke
paramixel.match.class.regex=Checkout
paramixel.match.package.regex=com\.example

# After (6.2.1) — for exact match (no change needed for tags)
paramixel.match.tag.regex=smoke
paramixel.match.class.regex=Checkout
paramixel.match.package.regex=com\.example

# After (6.2.1) — for substring match
paramixel.match.tag.regex=.*smoke.*
paramixel.match.class.regex=.*Checkout.*
paramixel.match.package.regex=com\.example.*
```

### 2. Update JVM system properties

```bash
# Before (6.2.x)
mvn test -Dparamixel.match.tag.regex=smoke

# After (6.2.1) — for exact match (no change needed)
mvn test -Dparamixel.match.tag.regex=smoke

# After (6.2.1) — for substring match
mvn test -Dparamixel.match.tag.regex=.*smoke.*
```

### 3. Update programmatic selector usage

```java
// Before (6.2.x)
var selector = Selector.tagRegex("smoke");
selector.matchesTag("smoke-test"); // true

// After (6.2.1) — exact match
var selector = Selector.tagRegex("smoke");
selector.matchesTag("smoke-test"); // false

// After (6.2.1) — substring match
var selector = Selector.tagRegex(".*smoke.*");
selector.matchesTag("smoke-test"); // true
```

## Recommended patterns

| Use case | Pattern |
| --- | --- |
| Exact tag match | `smoke` |
| Substring tag match | `.*smoke.*` |
| Exact class match | `com\.example\.MyTest` |
| Substring class match | `.*MyTest.*` |
| Package and subpackages | `com\.example.*` |
| Exact package only | `com\.example` |

## Why this is a bug

The `paramixel.match.*` property names specify "match" semantics (full-string match). The implementation used
`find()` semantics (substring match), violating the contract implied by the property names. This was not a design
choice — it was a bug. The fix aligns the implementation with the contract.
