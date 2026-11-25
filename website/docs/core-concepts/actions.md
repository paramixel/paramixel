---
title: Tasks
description: One immutable definition type for steps, groups, instance scopes, conditions, repetition, and timeouts.
---

# Tasks

`Task` is the only execution definition type in Paramixel 7. A step, a group, an instance scope, a conditional, a
repetition, and a timeout boundary are all the same immutable `Task` with different typed attributes.

```java
public final class Task {
    public String name();
    public ExecutionMode mode();          // SEQUENTIAL or PARALLEL
    public Optional<Pool> pool();
    public boolean dependent();
    public List<Task> children();
    public List<TaskCallback> before();
    public List<TaskCallback> after();
    public Optional<TaskCallback> body();
    public Optional<TaskFactory> instanceFactory();
    // condition, timeout, retry, repeat, exclusive lock, resources, tags, shuffle seed
}
```

A task holds no status, no fixture, and no timestamps. It is a definition, and a definition is reusable: each inclusion
under a parent compiles into a distinct execution occurrence with its own identity and its own owned instance.

## Body plus children

A task may have a body, children, or both. Its normal phase order is:

```
admission → condition → before → body → child group → after → terminal
```

The body runs before and independently of the children group; body success gates the children. Finalization never
depends on success: `after` hooks run in reverse declaration order and are all attempted.

```java
sequential(environment.name())
    .bind(ENVIRONMENT, environment)
    .before(ID_SET_UP)
    .child(sequential("Messaging")
            .independent()
            .child(sequential("Produce")
                    .body(ID_PRODUCE)
                    .child(sequential("Verify topic").independent().step(ID_VERIFY_TOPIC))
                    .child(sequential("Verify offsets").independent().step(ID_VERIFY_OFFSETS)))
            .step(ID_CONSUME))
    .after(ID_TEAR_DOWN);
```

## Sequential and parallel

`sequential` and `parallel` describe how a task's **direct children** run. `parallel` always names a `Pool`; a raw
string pool identifier does not compile.

```java
final var root = parallel(MySuite.class, "Compatibility matrix", Pool.named("environments"));
for (final var environment : environments) {
    root.child(sequential(environment.name())
            .bind(ENVIRONMENT, environment)
            .step(ID_VERIFY));
}
Task definition = root.build();
```

A parallel task has no `dependent()`/`independent()` method: it is always *attempt-all-unless-explicitly-canceled*.
Parallel siblings own separate context scopes, so they cannot see each other's entries; a fixture shared by several
branches is published with `bind` on the parent, and Paramixel does not implicitly synchronize calls on it.

## Dependent and independent

`dependent()` (the default) stops later direct children after any non-passing outcome, including `ABORTED`.
`independent()` attempts every eligible direct child in order. Both are failure-continuation policies, not concurrency
switches, and they affect only the direct children of the task that declares them.

## Attributes

| Attribute | Meaning |
| --- | --- |
| `timeout(Duration)` | Cooperative deadline for the task and its subtree |
| `retry(RetryPolicy)` | Bounded re-attempt of a non-success status |
| `child(repeat(name).maxIterations(n).until(id))` | Bounded intentional replay with an optional stop predicate |
| `onlyIf(id)` | Runtime condition evaluated after admission; the hook returns `boolean` |
| `isolated(lockName)` | Exclusive logical lock held for the task's lifetime |
| `tags(String…)` | Diagnostics, selection, and reporting metadata |
| `shuffle(seed)` / `shuffle()` | Reproducible child order, recorded in the result |

## Next

- [Lifecycle](../core-concepts/elements) — how instance ownership and finalization work.
- [Discovery](../core-concepts/discovery) — how factories become a root definition.
- [Data-driven testing](../core-concepts/data-driven-testing) — typed matrices with `each`.
