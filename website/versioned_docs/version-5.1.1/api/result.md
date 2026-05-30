---
title: Result
description: Result returned by a Paramixel run.
---

# Result

`org.paramixel.api.Result` describes the outcome of a Paramixel run.

## Interface

```java
public interface Result {
    Optional<Descriptor> descriptor();
    Status status();
}
```

## Properties

| Method | Description |
| --- | --- |
| `descriptor()` | The root descriptor of the executed action tree, or empty if no descriptor was created |
| `status()` | The effective aggregate status after tree aggregation and configuration promotion rules |

## Descriptor tree

The result points to the root `Descriptor`. A descriptor exposes:

- execution-occurrence metadata via `metadata()`
- navigation via `descriptor.parent()`, `descriptor.children()`, `descriptor.before()`, and `descriptor.after()`

Use descriptor metadata for execution state:

```java
Result result = runner.run(spec);

result.descriptor().ifPresent(root -> {
    var metadata = root.metadata();
    System.out.println(metadata.status() + " " + metadata.name());

    for (var child : root.children()) {
        var childMetadata = child.metadata();
        System.out.println(childMetadata.status() + " " + childMetadata.name());
    }
});
```

## Effective status

`Result#status()` aggregates the descriptor tree using Paramixel's severity ordering and then applies configuration rules:

- `paramixel.failureOnSkip=true` promotes skipped results to failed.
- `paramixel.failureOnAbort=true` promotes aborted results to failed. This is enabled by default.
- `paramixel.failIfNoTests=true` makes an empty discovery result fail.

Use `result.status()` for process exit decisions and CI outcomes. Use descriptor metadata when you need detailed per-action state.

## See also

- [Descriptor and Metadata](./descriptor-and-metadata)
- [Status](./status)
- [Runner](./runner)
