---
title: When to Use Paramixel
description: Decide when an execution tree is the right shape for a Java test.
---

# When to Use Paramixel

Paramixel is useful when the structure of the test matters as much as the assertions.

## Good fits

Use Paramixel when your test is naturally a workflow:

- setup and teardown exist at multiple levels
- sequential phases and parallel branches belong in the same test plan
- scenarios are generated from plain Java code
- each branch needs its own lifecycle
- failures should produce an inspectable result tree
- the result tree should mirror the action tree that was executed

Common examples include integration test workflows, compatibility matrices, service lifecycle checks, generated configuration scenarios, and tests that coordinate multiple external systems.

## Usually not needed

Paramixel is probably unnecessary when:

- a simple JUnit test method clearly expresses the behavior
- JUnit parameterized tests are sufficient
- the test only needs assertions or mocks
- no explicit lifecycle or execution structure is needed

This is intentional. Paramixel complements JUnit; it does not try to replace ordinary unit tests.

## Quick decision guide

| Question | Prefer JUnit-style tests | Prefer Paramixel |
| --- | --- | --- |
| Is the test one clear behavior? | Yes | Usually no |
| Does setup wrap a subtree of checks? | Usually indirect | Yes |
| Are branches generated in Java? | Sometimes | Yes |
| Do some branches run concurrently? | Usually externalized | Yes |
| Should reports mirror nested structure? | Limited | Yes |

## Next steps

- [Quick Start: Your First Execution Tree](first-test)
- [Core Concepts](../core-concepts)
- [Test Shapes](../test-shapes)
