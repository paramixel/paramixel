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
- `find` returns an empty optional, which is what teardown after a partially completed `before` hook should use.
- The framework never closes a stored value, and it never probes for `AutoCloseable`. Close resources in `after`;
  `remove` drops the reference early. Whatever is left when the scope ends is dropped for you.

## Bindings

`.bind(name, value)` seeds the scope at the start of every attempt of its owner, so a value captured while the
definition is authored is available to the hooks without being captured in a lambda. A name must be unique on its own
task; the same name may be bound by sibling tasks in a sequential chain, where seeding follows start order.
