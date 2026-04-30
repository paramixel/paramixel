---
title: Context
description: Runtime state, hierarchy, and attachments.
---

# Context

Every action receives a `Context` during execution.

## Methods

```java
Optional<Context> getParent()
Map<String, String> getConfiguration()
Listener getListener()
ExecutorService getExecutorService()
Optional<Context> findContext(int level)
<T> Context setAttachment(T attachment)
Optional<Attachment> getAttachment()
Optional<Attachment> removeAttachment()
Optional<Attachment> findAttachment(int level)
```

## Hierarchy

Contexts form a parent/child chain that mirrors nested execution.

- `getParent()` returns the immediate parent context
- `findContext(0)` returns the current context
- `findContext(1)` returns the parent
- larger levels walk farther up the chain
- `findContext(level)` throws `IllegalArgumentException` for negative levels
- `findContext(level)` throws `NoSuchElementException` when that ancestor does not exist

## Attachments

Each context can hold one attachment.

```java
context.setAttachment(new TestAttachment("value"));
```

Use the current context:

```java
TestAttachment attachment = context.getAttachment()
        .flatMap(a -> a.to(TestAttachment.class))
        .orElseThrow();
```

Use an ancestor context:

```java
TestAttachment attachment = context.findAttachment(1)
        .flatMap(a -> a.to(TestAttachment.class))
        .orElseThrow();
```

`findAttachment(level)` returns `Optional.empty()` only when the target ancestor exists but has no attachment. It also throws the same ancestor-navigation exceptions as `findContext(level)`.

## Example pattern

From `examples/test/context/ContextHierarchyTest.java`, a `before` action stores data and descendants read it later:

```java
Action action = Lifecycle.of(
        "test",
        Direct.of("before", context -> context.setAttachment(new TestAttachment("suite-value"))),
        Direct.of("main", context -> {
            TestAttachment attachment = context.findAttachment(1)
                    .flatMap(a -> a.to(TestAttachment.class))
                    .orElseThrow();
        }),
        Noop.of("after"));
```

## Configuration access

Prefer reading runtime settings from `context.getConfiguration()` inside actions rather than reaching out to JVM properties directly.
