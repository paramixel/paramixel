---
title: API Overview
description: Overview of Paramixel public packages.
---

# API Overview

Paramixel's public API is split into small packages.

| Package | Contents |
| --- | --- |
| `org.paramixel.api` | Runner, Configuration, Listener, Result, Status, Descriptor, `Paramixel` (nested annotations: `@Factory`, `@Disabled`, `@Tag`, `@Tags`, `@Priority`, `@Id`), `AnnotationResolver`, `Version`, `ThrowingRunnable`, `ThrowingConsumer` |
| `org.paramixel.api.action` | Action model — `Action`, `Spec`, `Step`, `Lifecycle`, `Sequential`, `Parallel`, `Instance`, `Static`, `Repeat`, `Timeout`, `Delay`, `AssertTrue`, `AssertFalse`, `Metadata`, `Context`, `Mode` |
| `org.paramixel.api.selector` | Selectors — `Selector`, `AndSelector`, `OrSelector`, `NotSelector`, `RegexSelector`, `TagRegexSelector`, `ClassRegexSelector`, `PackageRegexSelector` |
| `org.paramixel.api.exception` | Terminal/control exceptions — `FailException`, `SkipException`, `AbortedException`, `ConfigurationException`, `CycleDetectedException`, `ResolverException`, `PolicyException` |
| `org.paramixel.api.support` | Utilities — `Retry`, `Retry.Policy`, `Retry.Result`, `CleanUp` |

## Main entry points

- Use `Runner.defaultRunner()` to execute an action tree programmatically.
- Use `@Paramixel.Factory` on a `public static` no-argument factory method for classpath discovery.
- Use `Selector` to restrict discovery by package, class, or tag. Compose selectors with `Selector.and()`, `Selector.or()`, and `Selector.not()`.
- Use `Configuration` for built-in and custom `paramixel.*` properties.

## Action model

Actions are reusable definitions. Discovery creates a descriptor tree from those definitions. Execution updates each descriptor's `Metadata` with mode, status, duration, message, and throwable information.

## Custom actions

Custom actions implement `Action<T>` directly and use the SPI package for execution services. See [Custom Actions](../guides/custom-actions) for details.

## Version

`Version` provides the current Paramixel version string loaded from the classpath.

| Member | Description |
| --- | --- |
| `Version.version()` | Returns the version string, or `"UNKNOWN"` if the version resource is missing |
| `Version.UNKNOWN` | Fallback constant `"UNKNOWN"` returned when the version resource is missing or unreadable |

## Functional interfaces

| Interface | Package | Method | Description |
| --- | --- | --- | --- |
| `ThrowingRunnable` | `org.paramixel.api` | `void run() throws Throwable` | A runnable that may throw a checked exception |
| `ThrowingConsumer<T>` | `org.paramixel.api` | `void accept(T instance) throws Throwable` | A consumer that accepts an instance and may throw a checked exception |

These are used throughout the API: `ThrowingConsumer` in `Step`, `Lifecycle.Spec`, `Instance.Spec`, and `ThrowingRunnable` in `Static.Spec`, `Retry`, `CleanUp`.
