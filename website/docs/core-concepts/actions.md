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
admission → resources → instance → before → body → child group → after → release → terminal
```

The body runs before and independently of the children group; body success gates the children. Finalization never
depends on success: `after` callbacks run in reverse declaration order and are all attempted.

```java
forInstance(environment.name(), () -> new KafkaTest(environment))
    .before(KafkaTest::setUp)
    .sequential("Messaging", messaging -> messaging
        .step("Produce", KafkaTest::produce, children -> children
            .independent()
            .step("Verify topic", KafkaTest::verifyTopic)
            .step("Verify offsets", KafkaTest::verifyOffsets))
        .step("Consume", KafkaTest::consume))
    .after(KafkaTest::tearDown);
```

## Sequential and parallel

`sequential` and `parallel` describe how a task's **direct children** run. `parallel` always names a `Pool`; a raw
string pool identifier does not compile.

```java
Task definition = parallel("Compatibility matrix", Pool.named("environments"))
        .each(environments, env -> forInstance(env.name(), () -> new Probe(env))
                .step("Verify", Probe::verify))
        .build();
```

A parallel task has no `dependent()`/`independent()` method: it is always *attempt-all-unless-explicitly-canceled*.
Parallel siblings that share an inherited instance are concurrent calls on the same object; Paramixel does not
implicitly synchronize them.

## Dependent and independent

`dependent()` (the default) stops later direct children after any non-passing outcome, including `ABORTED`.
`independent()` attempts every eligible direct child in order. Both are failure-continuation policies, not concurrency
switches, and they affect only the direct children of the task that declares them.

## Attributes

| Attribute | Meaning |
| --- | --- |
| `timeout(Duration)` | Cooperative deadline for the task and its subtree |
| `retry(RetryPolicy)` | Bounded re-attempt of a non-success status |
| `repeat(name, r -> …)` | Bounded intentional replay with an optional stop predicate |
| `when(name, predicate, …)` / `onlyIf(predicate)` | Runtime condition evaluated after admission |
| `isolated(lockName)` | Exclusive logical lock held for the task's lifetime |
| `resource(Key, factory[, release])` | Typed secondary resource with reverse-order release |
| `tags(String…)` | Diagnostics, selection, and reporting metadata |
| `shuffle(seed)` / `shuffle()` | Reproducible child order, recorded in the result |

## Next

- [Lifecycle](../core-concepts/elements) — how instance ownership and finalization work.
- [Discovery](../core-concepts/discovery) — how factories become a root definition.
- [Data-driven testing](../core-concepts/data-driven-testing) — typed matrices with `each`.
