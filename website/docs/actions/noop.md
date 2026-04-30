---
title: Noop
description: An action that does nothing and passes.
---

# Noop

Factory:

```java
Noop.of(String name)
```

Use it when you need a placeholder action or an empty lifecycle phase.

```java
Action action = Noop.of("after");
```
