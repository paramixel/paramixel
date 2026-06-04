---
title: Data-Driven Testing
description: Build action trees from collections and streams.
---

# Data-Driven Testing

Use `Each.sequential()` and `Each.parallel()` to build action subtrees from `Iterable` or `Stream` inputs.

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

Use `Each.sequential` when order matters. Use `Each.parallel` when cases are independent and safe to run concurrently:

```java
import org.paramixel.api.action.Each;
import org.paramixel.api.action.Parallel;

Action parallelSpec = Each.parallel("browser matrix", browsers,
        browser -> Sequence.builder("check " + browser)
                .child(Step.of("run", ctx -> runBrowserScenario(browser)))
                .build())
        .parallelism(3)
        .build();
```

Keep data immutable or isolated per child when using parallel execution.
