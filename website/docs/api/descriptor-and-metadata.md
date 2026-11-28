---
title: Result Tree and Metadata
description: Stable identities, paths, tags, timings, and attempts.
---

# Result Tree and Metadata

The public read model is `Result` → `TaskResult` → `PhaseResult` / `Failure`. The old mutable `Descriptor` tree is gone;
there is one immutable post-run snapshot.

## Identity

| Identifier | Derived from | Stable across |
| --- | --- | --- |
| `runId` | Generated per run | — |
| `occurrenceId` | Structural ordinal path (`0`, `0.1`, `0.1.2`) | Identical plan structure |
| `definitionId` | Structural fingerprint of the definition node | Every placement of the same definition |
| `path` | Display names from the root | Identical definition |

Identifiers never incorporate thread identity, object identity hashes, wall-clock time, or completion order.

## Metadata

`TaskResult` exposes mode, pool name, exclusive lock, shuffle seed, resolved child order, tags, per-phase results, attempt
nodes, timings (queue, admission, start, completion), wait/execution/finalization durations, failures with the primary
failure first, and explicit reasons for skips, cancellations, and timeouts.

## Aggregation

`status()` is the aggregate over phases, body, children, and the terminal attempt. `bodyStatus()` is separate, so a
passed body never hides a failed child.
