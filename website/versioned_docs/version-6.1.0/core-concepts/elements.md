---
title: Composing Action Trees
description: How to compose actions and access fixtures through Context.
---

# Composing Action Trees

Paramixel composes tests from action classes under `org.paramixel.api.action`. Each action type is built through a `Builder` (or a static factory for terminal action types) and produces an immutable `Action` that composes into trees.

See [Actions](actions) for the complete list of 12 built-in action subtypes.

## Fixture access

`Context` exposes fixture instances by type where an `Instance` action provides the fixture:

```java
import org.paramixel.api.Context;
import org.paramixel.api.action.Step;

var verify = Step.of(
        "verify fixture",
        Context.withInstance(MyFixture.class, fixture -> fixture.verify()));
```

Use `context.instance(Type.class)` for an `Optional` or `context.requireInstance(Type.class)` when the fixture is required.

## Data-driven subtrees

`Each` is a utility class in `org.paramixel.api.action` for generating action subtrees from iterable inputs. It is not an `Action` subtype — it is a factory that produces action trees ready for composition.

```java
import java.util.List;
import org.paramixel.api.action.Each;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

var users = List.of("ada", "grace", "linus");

Action spec = Each.sequential("user checks", users,
        user -> Sequence.builder("check " + user)
                .child(Step.of("load", ctx -> loadUser(user)))
                .child(Step.of("verify", ctx -> verifyUser(user)))
                .build())
        .build();
```

See [Data-Driven Testing](data-driven-testing) for details on `Each.sequential()` and `Each.parallel()`.
