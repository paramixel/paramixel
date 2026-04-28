---
id: noop
title: Noop
description: Leaf action that completes immediately without doing any work
---

# Noop

`Noop` is a leaf action that completes immediately without doing any work.

## Creating Noop Actions

```java
import org.paramixel.core.action.Noop;

Action action = Noop.of("placeholder");
```

## Key Behavior

- Executes immediately and returns a `PASS` result
- Records execution timing like any other action
- Useful for placeholder actions, testing, or building action trees incrementally

## Example: Placeholder Action

```java
@Paramixel.ActionFactory
public static Action actionFactory() {
    return Sequential.of("MyTest",
        Direct.of("step 1", context -> setup()),
        Noop.of("step 2 (not implemented)"),  // Placeholder
        Direct.of("step 3", context -> cleanup()));
}
```

## Use Cases

- **Placeholder for unimplemented work** - Mark intended test steps
- **Testing action tree structure** - Verify tree composition without execution
- **Conditional skipping** - Programmatically replace real actions with Noop

## See Also

- [Action Composition](../usage/action-composition) - Building action trees incrementally
- [Direct](./direct) - Leaf action with a callback
- [Executable API](../api/intro) - Quick reference for Executable
