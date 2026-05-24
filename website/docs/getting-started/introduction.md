---
title: Introduction
description: What Paramixel is and how it works.
---

# Introduction

Paramixel is a tree-based test framework for Java 17+. Tests are composed as immutable `Action` trees that the framework executes and evaluates.

## How it works

Paramixel models test plans as **action trees** — hierarchical compositions of `Action` objects. Each action represents a discrete unit of test behavior: a single step, a sequential flow, a parallel group, or a lifecycle with setup and teardown.

The framework provides:

1. **Action composition** — Build test trees from `Step`, `Lifecycle`, `Sequential`, `Parallel`, `Instance`, `Static`, `Delay`, `Repeat`, `Timeout`, `AssertTrue`, and `AssertFalse` actions.
2. **Execution** — `Runner.run(action)` executes an action tree and returns a `Result` tree.
3. **Discovery** — `@Paramixel.Factory` methods are discovered from the classpath by `Runner`.
4. **Listeners** — `Listener` callbacks observe run and action lifecycle events.
5. **Reporting** — Built-in text, JSON, XML, and HTML report listeners.
6. **Configuration** — Layered configuration from `paramixel.properties`, JVM system properties, and built-in defaults.

## Action tree model

An action tree is an immutable hierarchy of `Action` instances:

```
Parallel "all-tests"
├── Lifecycle "user-service"
│   ├── Step "before" (setup)
│   ├── Step "create-user"
│   ├── Step "get-user"
│   └── Step "after" (teardown)
└── Sequential "product-service"
    ├── Step "list-products"
    └── Step "get-product"
```

Each node in the tree is an `Action`. Composite actions (`Lifecycle`, `Sequential`, `Parallel`, `Instance`, `Static`) contain child actions. Leaf actions (`Step`) execute a single callback.

## Execution statuses

Every action execution transitions through statuses:

| Status | Meaning |
| --- | --- |
| `PENDING` | Not yet started |
| `RUNNING` | Currently executing |
| `PASSED` | Completed without errors |
| `FAILED` | Completed with a thrown exception |
| `SKIPPED` | Deliberately skipped via `SkipException` |
| `ABORTED` | Failed precondition via `AbortedException` |

A descriptor starts `PENDING`, transitions to `RUNNING`, and then reaches exactly one terminal status (`PASSED`, `FAILED`, `SKIPPED`, or `ABORTED`).

Composite actions aggregate child statuses: `FAILED` > `ABORTED` > `RUNNING`/`PENDING` > `SKIPPED` > `PASSED`.

## What Paramixel is not

Paramixel is not an assertion library. Use AssertJ, JUnit assertions, or any library you prefer inside `Step` callbacks. Paramixel is the composition and execution engine — assertions are your choice.

## Next steps

- [Installation](./installation)
- [First Test](./first-test)
- [Project Setup](./project-setup)
