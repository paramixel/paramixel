---
title: Exception Reference
description: Outcome and framework exceptions.
---

# Exception Reference

Paramixel maps specific exceptions to action outcomes.

## Outcome exceptions

Outcome exceptions signal a deliberate non-pass outcome from step logic. The framework catches them and maps them to the corresponding terminal status.

- `FailException` communicates a **failed** outcome (`FAILED`) — an assertion did not hold. In a dependent `Sequential`, a failed child short-circuits the sequence: remaining children are skipped.
- `SkipException` communicates a **skipped** outcome (`SKIPPED`) — a deliberate, author-initiated choice not to run. In a dependent `Sequential`, a skipped child short-circuits the sequence (remaining children are skipped); elsewhere remaining siblings still run.
- `AbortedException` communicates an **aborted** outcome (`ABORTED`) — a precondition or assumption was not met, so no meaningful work was done and nothing was asserted. This is distinct from a failure (something was asserted false) and from a skip (a deliberate choice not to run).

A step that completes without throwing is recorded as passed. Other thrown exceptions may also be converted to failed statuses by framework execution.

### Effect of an aborted, skipped, or failed child on its siblings

How an outcome affects surrounding work depends on the enclosing action:

| Enclosing action | Aborted child | Skipped child | Failed child |
| --- | --- | --- | --- |
| `Sequential` (dependent) | does not short-circuit — siblings run; aggregates to `ABORTED` | short-circuits — remaining children skipped | short-circuits — remaining children skipped |
| `Sequential` (independent) | siblings run | siblings run | siblings run |
| `Parallel` | siblings run | siblings run | siblings run |
| `Loop` / `Until` | stops iterating — remaining iterations skipped; reports `ABORTED` | does not stop the loop | does not stop the loop (treated as "try again") |
| `Repeat` | siblings run | siblings run | siblings run |

:::note
`AbortedException` does **not** stop a dependent `Sequential`. To cancel the remaining children of a dependent sequence after a child, throw `FailException` instead.
:::

## Framework exceptions

- `ConfigurationException` for invalid configuration or loading failures.
- `CycleDetectedException` for cyclic action graphs.
- `ResolverException` for discovery or annotation resolution failures.
- `PolicyException` for invalid retry or policy configuration.

Outcome exceptions are the idiomatic way to signal a deliberate non-pass outcome from step logic; the framework converts them to the corresponding status for listeners and reporting.
