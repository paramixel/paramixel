---
title: Building Action Trees
description: How to compose actions and access fixtures through Context.
---

# Building Action Trees

Paramixel composes tests from action classes under `org.paramixel.api.action`. Each configurable action type is built through a `Builder`; terminal action types use static factory methods. The result is an immutable `Action` that can be nested into a larger execution tree.

See [Built-in Actions and When to Use Them](actions) for the complete list of 13 built-in action subtypes.

## Fixture access

A typed step receives its fixture directly; the type appears once, in `Instance.of`:

```java
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Step.step;

var fixture = of("my fixture", MyFixture.class, MyFixture::new)
        .before("start", MyFixture::start)
        .body(step("verify fixture", MyFixture.class, MyFixture::verify))
        .after("stop", MyFixture::stop);
```

Inside an untyped step, use `context.instance(Type.class)` for an `Optional` or `context.requireInstance(Type.class)`
when the fixture is required.

## Data-driven subtrees

`each(items, mapper)` on a container builder adds one child per item, in iteration order; it mixes with fixed
`child(...)` calls:

```java
import java.util.List;
import org.paramixel.api.action.Action;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

var users = List.of("ada", "grace", "linus");

Action spec = sequential("user checks")
        .each(users, user -> sequential("check " + user)
                .child(step("load " + user, ctx -> loadUser(user)))
                .child(step("verify " + user, ctx -> verifyUser(user))))
        .build();
```

```text
user checks
├── check ada
│   ├── load
│   └── verify
├── check grace
│   ├── load
│   └── verify
└── check linus
    ├── load
    └── verify
```

See [Data-Driven Testing](data-driven-testing) for the `each` semantics and the fixture-per-item shape.
