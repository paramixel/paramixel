---
title: Attachment
description: Typed access to context attachments.
---

# Attachment

`Attachment` is the wrapper returned by `Context#getAttachment()`, `removeAttachment()`, and `findAttachment(int)`.

## Methods

```java
Class<?> getType()
<T> Optional<T> to(Class<T> type)
```

## Behavior

- a context stores at most one attachment value; setting `null` clears the attachment
- `setAttachment(value)` replaces the current value
- `getAttachment()` only reads the current context
- `findAttachment(level)` reads the current or ancestor context
- `removeAttachment()` removes the current context's value and returns it as an `Attachment`

If no attachment is present on the target context (never set, or cleared via `setAttachment(null)` or `removeAttachment()`), the `Context` methods return `Optional.empty()`.
`findAttachment(level)` still throws if `level` is negative or the ancestor does not exist.

`Attachment#to(...)`:

- returns `Optional.empty()` only when the underlying value is absent
- otherwise uses `Class#cast`, so an incompatible type causes `ClassCastException`

## Example

```java
context.setAttachment(new TestAttachment("value"));

String value = context.getAttachment()
        .flatMap(a -> a.to(TestAttachment.class))
        .map(TestAttachment::value)
        .orElseThrow();
```

## Ancestor lookup

Attachments are not globally shared. A child action can only reach ancestor data by navigating the context hierarchy.

```java
TestAttachment attachment = context.findAttachment(1)
        .flatMap(a -> a.to(TestAttachment.class))
        .orElseThrow();
```
