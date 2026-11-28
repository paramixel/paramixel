---
title: Result
description: The immutable outcome of a run, and the single source of truth for reporting.
---

# Result

```java
public final class Result { /* immutable; obtain it from a Runner run */ }
```

| Accessor | Meaning |
| --- | --- |
| `runId()` | Stable identifier of the run |
| `status()` | Semantic aggregate status, never rewritten by exit policy |
| `exitCode()` | Process exit code after `failureOnSkip`, `failureOnAbort`, `failIfNoTests`, and infrastructure failures |
| `isQuiescent()` | `false` when task work may still be running and cleanup was withheld; independent of listener delivery |
| `root()` | `Optional<TaskResult>` root of the immutable tree; empty when nothing executed |
| `failures()` | Run-level infrastructure failures |
| `diagnostics()` | Warnings and infrastructure notes |
| `configurationSummary()` | Redacted summary of the effective Paramixel configuration |
| `occurrenceCount()` / `attemptCount()` | Logical occurrences and attempt nodes; status counts sum to `occurrenceCount()` |

## TaskResult

```text
TaskResult
  kind (TASK or ATTEMPT), occurrenceId, definitionId, parentOccurrenceId, name, path, tags
  parameters (the node's rendered bind values), slot (CHILD, BEFORE, AFTER)
  mode, poolName, exclusiveLock, shuffleSeed, executionOrder
  status, bodyStatus, phases, children, failures, reason, attemptIndex, timedOut
  queuedAt, admittedAt, startedAt, completedAt, wait/execution/finalization durations
```

`parameters()` holds the node's plan parameters: the values it declared with `bind`, rendered at build time. They are
never copied onto descendants, and a value stored at runtime with `Context.put` is not a parameter.

On a discovered factory's root occurrence and its attempts, `tags()` contains builder tags followed by missing
annotation tags. Other tasks report their builder tags. Tags are available in the result API.

`slot()` says whether the node is an ordinary child of its parent or that parent's `before` or `after` subtree. A phase
subtree is reported under its owner, with its own children beneath it.

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
nodes for retry history, and the status counters for occurrence outcomes. If listener delivery stalls, the event stream
and configured reports may be incomplete. The result returned by `Runner` remains authoritative for task outcomes and
includes a `LISTENER_DRAIN_TIMEOUT` infrastructure failure and a nonzero exit code.
