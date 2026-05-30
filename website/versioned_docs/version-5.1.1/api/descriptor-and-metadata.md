---
title: Descriptor and Metadata
description: Execution-occurrence state for action trees — descriptor navigation and live metadata.
---

# Descriptor and Metadata

A `Descriptor` represents one bound occurrence of an `Action` in a run descriptor tree. `Metadata` carries the per-occurrence identity and live execution state for that descriptor.

Reusing the same `Action` instance in multiple locations creates distinct descriptors with independent execution state.

```java
import org.paramixel.api.Descriptor;
import org.paramixel.api.action.Metadata;
```

## Descriptor

```java
public interface Descriptor {
    Optional<Descriptor> parent();
    Metadata metadata();
    default Optional<Descriptor> before();
    List<Descriptor> children();
    default Optional<Descriptor> after();
}
```

| Method | Description |
| --- | --- |
| `parent()` | The parent descriptor, or empty for the root |
| `metadata()` | The per-occurrence identity and execution state |
| `before()` | The before-child descriptor (Lifecycle, Static, Instance); not included in `children()` |
| `children()` | The body child descriptors in discovery order; does not include before or after |
| `after()` | The after-child descriptor (Lifecycle, Static, Instance); not included in `children()` |

Descriptors are read-only through this interface. The framework creates and mutates descriptors during discovery and execution.

### Tree navigation

```java
void printTree(Descriptor descriptor, String indent) {
    var meta = descriptor.metadata();
    System.out.println(indent + meta.name() + " [" + meta.kind() + "] " + meta.status().name());
    descriptor.before().ifPresent(before -> printTree(before, indent + "  "));
    for (Descriptor child : descriptor.children()) {
        printTree(child, indent + "  ");
    }
    descriptor.after().ifPresent(after -> printTree(after, indent + "  "));
}
```

## Metadata

```java
public interface Metadata {
    String id();
    String name();
    String kind();
    String className();
    Status status();
    Mode mode();
    Duration runDuration();
    Optional<String> message();
    Optional<Throwable> throwable();
    boolean isCompleted();
}
```

| Method | Description |
| --- | --- |
| `id()` | Generated execution-occurrence identifier, unique across the current run |
| `name()` | The action display name |
| `kind()` | The action kind (e.g. `"Step"`, `"Lifecycle"`, `"Parallel"`) |
| `className()` | The fully qualified runtime class name of the action |
| `status()` | The current execution status |
| `mode()` | The execution mode (`RUN`, `SKIP`, `ABORT`) |
| `runDuration()` | The local run duration measured for this descriptor |
| `message()` | The optional status message |
| `throwable()` | The optional throwable recorded for this descriptor |
| `isCompleted()` | Whether the descriptor has reached a terminal status |

### Identity vs state

Identity fields (`id()`, `name()`, `kind()`, `className()`) are immutable. State fields (`status()`, `mode()`, `runDuration()`, `message()`, `throwable()`, `isCompleted()`) are updated during execution and are thread-safe.

### Execution identifier

The identifier is generated per execution occurrence and is unique across the current run. Running the same `Action` instance twice produces different identifiers. The `@Paramixel.Id` annotation and `AnnotationResolver.byId()` are separate discovery concepts and do not imply execution identifiers.

See [Result](./result) for how to access the root descriptor from a run result.
