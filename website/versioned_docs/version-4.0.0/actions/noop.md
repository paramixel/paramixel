---
title: Noop
description: An action that does nothing and passes.
---

# Noop

`Noop` does no work and passes. It is the only built-in action with a compact static factory in 4.x:

```java
Action action = Noop.of("placeholder");
```

Use it when you want an explicit placeholder/result node. Optional `Container.before(...)` and `Container.after(...)` do not require `Noop` placeholders.

Factory:

```java
Noop.of(String name)
```

Use it when you need a placeholder action or an empty lifecycle phase.

```java
Action action = Noop.of("after");
```
