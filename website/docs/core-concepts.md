---
title: Core Concepts
description: Definitions, occurrences, attempts, and results.
---

# Core Concepts

Paramixel separates four things that are easy to conflate:

| Concept | What it is |
| --- | --- |
| **Definition** | An immutable `Task` tree you author once and may reuse |
| **Occurrence** | One placement of a definition in a compiled plan, with its own identity and scope |
| **Attempt** | One execution attempt of an occurrence; retry and repeat create more than one |
| **Result** | An immutable snapshot of what happened |

## The tree

Definitions form an ordered containment tree. The compiler lowers it into scheduling edges and resource requirements: an
ordered child group, success gates for bodies and children, and finalizers attached to their owning task. The scheduler
never lets a parent finish while its children or finalizers are live.

## Building and compiling are free

Authoring, building, and compiling never invoke a factory, lifecycle callback, body, or runtime predicate. Consequences:

- A definition can be validated without side effects.
- A definition can be reused across runs and runner instances.
- A validation failure means no user code ran at all.

## Execution

Occurrences are admitted in deterministic depth-first order subject to resource eligibility. Named `Pool`s bound how
many children of a parallel group are alive at once; exclusive locks serialize groups; waiting never occupies a worker.

## Outcomes

`Result.status()` is the semantic aggregate. `Result.exitCode()` applies the configured mapping. `Result.isQuiescent()`
is `false` only when a callback ignored cooperative cancellation, in which case cleanup was withheld and the run is not
reported as cleanly finalized.

Continue with:

- [Tasks](core-concepts/actions)
- [Lifecycle and scope](core-concepts/elements)
- [Discovery](core-concepts/discovery)
- [Data-driven testing](core-concepts/data-driven-testing)
