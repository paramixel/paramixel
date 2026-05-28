---
title: Migration 5.0 to 5.1
description: Breaking API and behavior changes from 5.0.0 to 5.1.0.
---

# Migration 5.0 to 5.1

This guide covers the breaking API and behavior changes when migrating from Paramixel 5.0.0 to 5.1.0.

**For most common usage scenarios, no changes are required.**

## ExecutionContext renamed to Context

The `ExecutionContext` interface has been renamed to `Context` and moved from the SPI package to the public API package.

| 5.0.0 | 5.1.0 |
| --- | --- |
| `org.paramixel.spi.action.ExecutionContext` | `org.paramixel.api.action.Context` |
| `ExecutionContext` (all references) | `Context` |

```java
// 5.0.0
import org.paramixel.spi.action.ExecutionContext;

public void execute(ExecutionContext context) { /* ... */ }

// 5.1.0
import org.paramixel.api.action.Context;

public void execute(Context context) { /* ... */ }
```

## Selector redesign

`Selector` has been redesigned from a concrete class into an interface in a new subpackage, with a rich composition API.

| 5.0.0 | 5.1.0 |
| --- | --- |
| `org.paramixel.api.Selector` (class) | `org.paramixel.api.selector.Selector` (interface) |
| Constructor-based | Static factory methods |

### New factory methods

```java
Selector.all()
Selector.packageRegex(String)
Selector.classRegex(String)
Selector.tagRegex(String)
Selector.packageTreeOf(Class<?>)
Selector.packageOf(Class<?>)
Selector.classOf(Class<?>)
```

### New composition methods

```java
Selector.and(Selector...)
Selector.or(Selector...)
Selector.not(Selector)
```

### New instance methods

```java
selector.matchesPackage(String)
selector.matchesClass(String)
selector.matchesTag(String)
```

### New sub-interfaces

| Interface | Extends | Added method |
| --- | --- | --- |
| `RegexSelector` | `Selector` | `pattern()` |
| `PackageRegexSelector` | `RegexSelector` | — |
| `ClassRegexSelector` | `RegexSelector` | — |
| `TagRegexSelector` | `RegexSelector` | — |
| `AndSelector` | `Selector` | `selectors()` |
| `OrSelector` | `Selector` | `selectors()` |
| `NotSelector` | `Selector` | `selector()` |

## Resolver removed

`org.paramixel.api.Resolver` is removed. Discovery is now an internal concern of `Runner`. Use the `Runner` methods that accept a `Selector`:

```java
// 5.0.0
Resolver resolver = new Resolver(classLoader);
List<Action<?>> actions = resolver.resolve(selector);

// 5.1.0
Optional<Result> result = runner.run(selector);
int exitCode = runner.runAndReturnExitCode(selector);
```

## Removed classes

| Removed | Replacement |
| --- | --- |
| `AbstractAction` | Implement `Action` directly — see [Action](../api/action) |
| `CompositeAction` | `Lifecycle`, `Sequential`, `Parallel`, `Instance` implement `Action` directly |
| `Builder` | Use `Spec` accumulators — see [Spec](../api/spec) |
| `ActionMetadata` | `Metadata` — see [Descriptor and Metadata](../api/descriptor-and-metadata) |
| `Resolver` | Use `Runner.run(Selector)` or `Runner.run()` |
| `Cleanup` + `CleanupResult` | `CleanUp` — see below |

## Cleanup replaced by CleanUp

`Cleanup` and `CleanupResult` are replaced by `CleanUp` with a simplified API:

| 5.0.0 | 5.1.0 |
| --- | --- |
| `org.paramixel.api.support.Cleanup` | `org.paramixel.api.support.CleanUp` |
| `Cleanup.Mode` enum | Removed — `CleanUp` is single-use |
| `CleanupResult` per-callback result | `CleanUp.throwable()` — single nullable throwable |
| `Cleanup.of(Runnable)` | `CleanUp.of(ThrowingRunnable)` |
| `Cleanup.of(AutoCloseable)` | `CleanUp.of(AutoCloseable)` |

```java
// 5.0.0
Cleanup cleanup = Cleanup.of(() -> connection.close());
CleanupResult result = cleanup.run();
if (result.getThrowable() != null) { /* handle */ }

// 5.1.0
CleanUp cleanup = CleanUp.of(() -> connection.close());
cleanup.run();
if (cleanup.isNotEmpty()) { /* handle */ }
```

## Retry method renames

`Retry.Policy` and `Retry.Result` methods were renamed to follow Java record-style naming:

| 5.0.0 | 5.1.0 |
| --- | --- |
| `Policy.getMaximumDuration()` | `Policy.maximumDuration()` |
| `Result.getMaximumDuration()` | `Result.maximumDuration()` |
| `Result.getAttemptCount()` | `Result.attemptCount()` |
| `Result.getElapsedDuration()` | `Result.elapsedDuration()` |
| `Result.getException(int)` | `Result.exception(int)` |
| `Result.getExceptions()` | `Result.exceptions()` |

Additionally, `Retry` is now `final` and `Policy.waitDuration()` and `maximumDuration()` declare `@throws PolicyException`.

## Action interface changes

### kind() is now required

Custom `Action` implementations must implement `kind()`. In 5.0.0, `kind()` was a default method; in 5.1.0 it remains a default method returning `"Action"`, but the framework and console output expect a meaningful kind. All built-in actions return their simple type name (e.g. `"Step"`, `"Lifecycle"`, `"Parallel"`).

### New default methods

| Method | Default | Description |
| --- | --- | --- |
| `kind()` | `"Action"` | Returns the action kind for console output and reports |
| `before()` | `Optional.empty()` | Before-child action (Lifecycle, Static) |
| `children()` | `List.of()` | Body child actions |
| `after()` | `Optional.empty()` | After-child action (Lifecycle, Static) |
| `resolve()` | `this` | Leaf actions return themselves; specs create immutable instances |

## Listener callback additions

Two new callbacks were added to the `Listener` interface:

| Callback | Description |
| --- | --- |
| `initialize(Configuration)` | Invoked once before the run begins; allows listeners to configure themselves |
| `onDiscoveryStarted()` | Invoked after `onRunStarted()`, before the discovery phase |

Both have default no-op implementations. Existing `Listener` implementations compile without changes.

## AnnotationResolver custom-kind overloads

New overloads allow specifying a custom kind on resolved actions:

```java
// 5.0.0
resolver.byId("login")            // kind defaults to "Step"
resolver.staticById("setup")     // kind defaults to "Step"

// 5.1.0
resolver.byId("login", "LoginStep")
resolver.staticById("setup", "SetupStep")
```

## @Paramixel.Factory null return

The `@Paramixel.Factory` annotation now explicitly documents that returning `null` from a factory method indicates the factory should be skipped, producing a skipped action outcome at runtime.

## Internal package namespace

Internal support classes moved from `org.paramixel.internal.*` to `nonapi.org.paramixel.*`. These are not public API, but if you imported internal types, update the package prefix.

## Automatic-Module-Name

The core JAR manifest now includes `Automatic-Module-Name: org.paramixel.api` for JPMS automatic module naming.

## New configuration keys

| Key | Description |
| --- | --- |
| `paramixel.listener.exclude` | Comma-separated tokens to suppress listener output sections (`status.header`, `status.footer`, `status`, `summary.header`, `summary.tree`, `summary.footer`, `quiet`, `all`) |
| `paramixel.match.package.regex` | Regex for filtering discovered factories by package name |
| `paramixel.match.class.regex` | Regex for filtering discovered factories by class name |
| `paramixel.match.tag.regex` | Regex for filtering discovered factories by tag value |

## New exception

| Exception | Description |
| --- | --- |
| `PolicyException` | Signals retry `Policy` computation failure |

## Quick migration checklist

1. Replace `ExecutionContext` with `Context` and update imports from `org.paramixel.spi.action` to `org.paramixel.api.action`
2. Update `Selector` imports from `org.paramixel.api.Selector` to `org.paramixel.api.selector.Selector`
3. Replace `Selector` constructor usage with static factory methods (`Selector.all()`, `Selector.packageRegex(...)`, etc.)
4. Remove `Resolver` usage — use `Runner.run(Selector)` or `Runner.run()`
5. Replace `Cleanup` + `CleanupResult` with `CleanUp`
6. Rename `Retry.Policy.getMaximumDuration()` to `maximumDuration()` and other `get*` method renames
7. Implement `kind()` on custom `Action` implementations
8. Replace `AbstractAction` and `CompositeAction` with direct `Action` implementation
9. Replace `Builder` with `Spec` accumulators
10. Replace `ActionMetadata` with `Metadata`
11. Update any `org.paramixel.internal.*` imports to `nonapi.org.paramixel.*`
