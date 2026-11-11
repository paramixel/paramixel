---
title: Context
description: Read-only execution metadata and explicitly keyed resources.
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
    Object primaryInstance();
    <T> T require(Key<T> key);
    <T> Optional<T> find(Key<T> key);
}
```

A context belongs to one occurrence of one attempt. It exposes stable identity, the effective configuration, the nearest
lexical primary instance, and keyed resources; it never exposes mutable execution state.

## Primary instances

Typed callbacks receive the instance directly. `primaryInstance()` exists for framework adapters and advanced code; a
nested `forInstance` establishes its own binding and does not make the outer instance appear here.

## Keyed resources

```java
Key<HttpClient> CLIENT = Key.named("http", "client", HttpClient.class);

Task definition = task("root")
        .resource(CLIENT, HttpClient::new, HttpClient::close)
        .stepWithContext("call", (Context context) ->
                context.require(CLIENT).send(request));
```

- Acquisition is lazy, at admission, in declaration order.
- Release runs in reverse acquisition order after `after` callbacks.
- A missing required resource throws `ResourceException` naming the key and occurrence path.
- Duplicate keys in one scope and ambiguous shadowing across scopes are rejected at compile time.

## Context-aware callbacks

Use the explicitly named methods — `beforeWithContext`, `afterWithContext`, `stepWithContext` — rather than an ambiguous
overload of the one-argument callbacks.
