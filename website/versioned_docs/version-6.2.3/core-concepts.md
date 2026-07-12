---
title: Core Concepts
description: Vocabulary for Paramixel execution trees.
---

# Core Concepts

Read this page before the API reference. It gives names to the pieces of a Paramixel test plan.

## Action

An action is a node in a Paramixel execution tree. Every action has a display name and execution behavior.

## Action tree

An action tree is the complete executable test plan. Java code builds the tree, Paramixel executes the tree, and the result tree mirrors the action tree.

```text
OrderWorkflow
├── start services
├── run checks
│   ├── create order
│   ├── read order
│   └── cancel order
└── stop services
```

## Leaf action

A leaf action performs work directly and has no child actions. Examples include a step, an assertion, or a delay.

## Composite action

A composite action contains child actions and controls how those children run. Some composites run children in order, some run independent branches concurrently, and some wrap a subtree with lifecycle behavior.

## Lifecycle

Lifecycle makes setup and teardown explicit in the tree. A lifecycle subtree shows which setup belongs to which checks and which teardown runs afterward.

```text
DatabaseWorkflow
├── before: start database
├── body
│   ├── migrate schema
│   └── verify query
└── after: stop database
```

## Context

Context carries runtime state during execution. Actions can use it to read configuration, access fixture instances, and interact with framework-provided execution services.

## Result tree

After execution, Paramixel returns a result tree with the same shape as the action tree. Each result node records the status and metadata for the corresponding action occurrence.

## Learn the APIs next

- [Building Action Trees](core-concepts/elements)
- [Built-in Actions and When to Use Them](core-concepts/actions)
- [Result API](api/result)
- [Descriptor and Metadata](api/descriptor-and-metadata)
