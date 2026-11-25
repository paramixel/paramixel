---
title: Context
description: Read-only execution metadata plus the per-scope state store.
---

# Context

```java
public interface Context {
    Configuration configuration();
    String runId();
    String occurrenceId();
    Optional<String> parentOccurrenceId();
    String name();
    String path();
    int attempt();
    List<String> tags();
    boolean isCanceled();
    Optional<String> cancellationReason();
    void put(String name, Object value);
    void remove(String name);
    <T> T require(String name, Class<T> type);
    <T> Optional<T> find(String name, Class<T> type);
}
```

A context belongs to one occurrence of one attempt. It exposes stable identity, the effective configuration, and a state
store shared by every occurrence in the same **scope**.

## Scope state

```java
@Paramixel.Id("allocate")
public static void allocate(final Context context) {
    context.put("client", new HttpClient());          // stored in this scope
}

@Paramixel.Id("call")
public static void call(final Context context) {
    context.require("client", HttpClient.class).send(request);
}

@Paramixel.Id("deallocate")
public static void deallocate(final Context context) {
    context.find("client", HttpClient.class).ifPresent(HttpClient::close);
}
```

A scope is created at the run root, at every direct child of a `parallel` group, and at every task carrying `retry` or
`repeat`. Sequential descendants share the nearest enclosing scope, so a value stored by one step is visible to the next
sibling and to the group's `after` hook; parallel siblings never see each other's entries.

- A missing entry throws `ContextException` naming the entry and occurrence path; a type mismatch names both types.
- `find` returns an empty optional, which is what teardown after a partially completed `before` slot should use.
- The framework never closes a stored value, and it never probes for `AutoCloseable`. Close resources in `after`;
  `remove` drops the reference early. Whatever is left when the scope ends is dropped for you.

## Stored values versus bindings

`put` and `bind` answer different questions, and only one of them is reported:

| | `bind(name, value)` | `put(name, value)` |
| --- | --- | --- |
| What it holds | Data that already exists when the plan is built | A resource created while the test runs |
| Where it lives | The declaring node's subtree | The scope of the occurrence that writes it |
| Reported | Yes: a plan parameter in report files | Never |
| Precedence | Lower: a `put` of the same name shadows it | Higher |

`bind` is visible from its node downward, nearest declaration first, so a child that binds the same name shadows the
ancestor's value inside its own subtree only. A name must be unique on its own node. Because a binding is a plan
parameter rather than a scope entry, `remove` does not delete it.

`put` writes into the nearest scope owner: the run root, a direct child of a `parallel` group, or a task carrying
`retry` or `repeat`. A write from an attempt that was already superseded — a timeout, a cancellation, or a hook that
ignored interruption — is discarded.
