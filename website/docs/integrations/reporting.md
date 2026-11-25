---
title: Reporting
description: Console, JSON, XML, HTML, and text reports from one immutable result.
---

# Reporting

Every reporter reads the same immutable `Result`, so console output, report files, and integrations cannot disagree.

## Console

`Listener.defaultListener(configuration)` wires the status lines and the summary tree. Sections can be suppressed with
`paramixel.listener.exclude` (`status`, `summary.tree`, `quiet`, `all`, or individual section names). Colors follow
`paramixel.ansi` (`true`, `false`, `auto`).

Every console line begins with `[PARAMIXEL] `; the word `PARAMIXEL` is blue when ANSI output is enabled. Status lines use
an unpadded short label followed by one space, and they end with the occurrence's elapsed time:

```text
[PARAMIXEL] PASS KafkaTest > apache/kafka:3.7.2 > tests > produce() (42 ms)
[PARAMIXEL] FAIL Probe > verify (3 ms) — java.lang.AssertionError: boom
[PARAMIXEL] PASS run 5c0f… in 1 s 240 ms
```

Framework scaffolding added by discovery (the synthetic root, the hook-gating group, and the factory group) is excluded
from display paths, so a factory's path starts at the factory name instead of repeating a constant framework prefix.
Levels of a display path are separated by ` > `, so a slash inside a task name (a Docker image, URL, or file path) never
looks like a level boundary. Stable identity is `occurrenceId`, never the path.

Labels are `PASS`, `FAIL`, `SKIP`, `ABORT`, and `CANCEL`; `PEND`/`RUN` are used for in-flight kinds. Status lines are
not padded, so paths are not column-aligned across different statuses.

The console summary is introduced by a title bracketed in horizontal rules, and the run totals are separated from the
result tree by a rule. The summary shows each occurrence once with its final outcome, summarizing retry or repeat
history on the occurrence line, so console output never contradicts the counters. The tree is drawn from the left margin
with box-drawing connectors (`├──`, `└──`, `│`); each line is the task name and its qualifiers, then the outcome, elapsed
time, and reason. Because the status follows the name, connectors align without padding labels, and a summary line does
not begin with a status label the way a live status line does. Only a task that schedules children carries a
`(sequential)` or `(parallel)` suffix: a leaf task runs its own body, so naming a scheduling mode for it would claim
something that never happens:

```text
[PARAMIXEL] PASS run 5c0f… in 1 s 240 ms
[PARAMIXEL] -----------------
[PARAMIXEL] Paramixel Summary
[PARAMIXEL] -----------------
[PARAMIXEL] retry-example (sequential) PASS (1200 ms) — 3 attempts, 2 failed
[PARAMIXEL] └── retry PASS (400 ms)
[PARAMIXEL] -------
[PARAMIXEL] occurrences: 2 passed: 2 failed: 0 skipped: 0 aborted: 0 canceled: 0 timedOut: 0 attempts: 3
[PARAMIXEL] result: PASS exitCode: 0 elapsed: 1 s 240 ms
```

Per-node elapsed time is whole milliseconds, the same unit as a live status line, and a node that never ran has no time.
The compact hour/minute/second form is used only for the run footer and totals. The text report renders the same tree,
including the `attempt k of n` nodes and `[before]`/`[after]` phase markers.

Report files, by contrast, keep every attempt. Each attempt is labeled `attempt k of n`, and superseded attempts are
marked `superseded` in the text and HTML reports and with `"superseded": true` plus `"attemptTotal"` in JSON.

## Report files

Set `paramixel.report.file`. The format follows the extension:

| Extension | Format |
| --- | --- |
| `.json` | Machine-readable tree with run metadata, statuses, phases, failures, counters |
| `.xml` | Well-formed tree with the same identities |
| `.html` / `.htm` | Status-colored table |
| anything else | Printable tree text |

Reports include run identifiers, occurrence and definition identifiers, paths, plan parameters, statuses, explicit
reasons, stack traces, redacted configuration, and counters. They never include the full environment.

### Schema version 2

Every structured report carries `schemaVersion: 2`. Version 2 added three things:

| Field | Where | Meaning |
| --- | --- | --- |
| `parameters` | JSON object on each node, immediately after `path`; `<parameter name="…" value="…"/>` children in XML; a collapsed detail row in HTML | The values the node declared with `bind`, rendered once at build time with `String.valueOf` and truncated at 256 characters plus `…`. `{}` when the node binds nothing. |
| `slot` | JSON field and XML attribute on each node | `CHILD`, `BEFORE`, or `AFTER`: a `before`/`after` subtree is reported under its owner and marked with its phase. |
| Phase nodes | Console and report trees | A subtree given to a `before` or `after` slot appears as a node with its own children. An identified hook stays a phase of its owner (`before[setUp]`). |

Plan parameters describe the plan. Values a test stores at runtime with `Context.put` are never reported, and parameters
are never printed in console output.

## Metrics

`Result` publishes occurrence, attempt, passed, failed, skipped, aborted, canceled, and timeout counts. Counters include
container occurrences, so the status counters sum to `occurrenceCount()`. Each logical occurrence is counted once: a
successful retry reports one passed occurrence plus its `attemptCount()`, and the failed steps of earlier attempts remain
visible only as `ATTEMPT` nodes — a passing run never reports failed occurrences because it retried.

## Custom listeners

Implement `Listener` and pass it to `Runner.builder().listener(...)`. Delivery is asynchronous on one dispatcher thread,
bounded, and isolated: a listener exception is recorded as a diagnostic and never changes a task status. A full queue
pauses new work without dropping events while delivery is making progress. A delivery stall fails the run at the process
boundary without rewriting completed task outcomes; an incomplete stream does not receive `onRunCompleted`.
