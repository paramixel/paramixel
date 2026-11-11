---
title: Result
description: The immutable outcome of a run, and the single source of truth for reporting.
---

# Result

```java
public interface Result { /* read-only accessors */ }
```

| Accessor | Meaning |
| --- | --- |
| `runId()` | Stable identifier of the run |
| `status()` | Semantic aggregate status, never rewritten by exit policy |
| `exitCode()` | Process exit code after `failureOnSkip`, `failureOnAbort`, and `failIfNoTests` |
| `isQuiescent()` | `false` when work may still be running and cleanup was withheld |
| `root()` | Immutable `TaskResult` tree |
| `failures()` | Run-level infrastructure failures |
| `diagnostics()` | Warnings and infrastructure notes |
| `configurationSummary()` | Redacted configuration actually used |
| `occurrenceCount()` / `attemptCount()` | Logical occurrences and attempt nodes; status counts sum to `occurrenceCount()` |

## TaskResult

```text
TaskResult
  kind (TASK or ATTEMPT), occurrenceId, definitionId, parentOccurrenceId, name, path, tags
  mode, pool, lock, shuffleSeed, executionOrder
  status, bodyStatus, phases, children, failures, reason, attemptIndex, timedOut
  queuedAt, admittedAt, startedAt, completedAt, wait/execution/finalization durations
```

Two facts are deliberately separate: `status()` is the aggregate including children and finalizers, while
`bodyStatus()` is the outcome of the task's own body. A passed body cannot conceal a failed subtree.

A retried or repeated occurrence contains `ATTEMPT` children with their own statuses, timings, and failures, so a
successful retry keeps its historical failures visible while the final aggregate passes.

The counters count each **logical occurrence once**: a retried task contributes one passed occurrence and increments
`attemptCount()`, while the failed steps inside earlier attempts stay visible in the tree as `ATTEMPT` children and are
not counted as failed occurrences. A passing run therefore reports `failedCount() == 0` even when it retried.

## Statuses

`PASSED`, `FAILED`, `SKIPPED`, `ABORTED`, `CANCELED`. Aggregation is
`FAILED > CANCELED > ABORTED > SKIPPED > PASSED`, so a group with passed and skipped children aggregates to `SKIPPED`
rather than hiding the partial execution.

## Reporting

Console output, JSON, XML, HTML, and text reports are generated from the same `Result`. Report formats follow the file
extension configured with `paramixel.report.file`; secrets are redacted and the full environment is never dumped.
Reporters must distinguish a successful retry from its historical attempts: use `attemptCount()` and the `ATTEMPT`
nodes for retry history, and the status counters for occurrence outcomes.
