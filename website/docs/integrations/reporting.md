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
[PARAMIXEL] PASS KafkaTest / Standalone(amazoncorretto:24) / tests / testHealthy() (42 ms)
[PARAMIXEL] FAIL Probe / verify (3 ms) — java.lang.AssertionError: boom
[PARAMIXEL] PASS run 5c0f… in 1 s 240 ms
```

Framework scaffolding added by discovery (the synthetic root, the hook-gating group, and the factory group) is excluded
from display paths, so a factory's path starts at the factory name instead of repeating a constant framework prefix.
Stable identity is `occurrenceId`, never the path.

Labels are `PASS`, `FAIL`, `SKIP`, `ABORT`, and `CANCEL`; `PEND`/`RUN` are used for in-flight kinds. Because labels are
not padded, paths are not column-aligned across different statuses.

The console summary shows each occurrence once with its final outcome and summarizes retry or repeat history on the
occurrence line, so console output never contradicts the counters:

```text
[PARAMIXEL] PASS retry-example (sequential) — 3 attempts, 2 failed
[PARAMIXEL] PASS   retry (sequential)
```

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

Reports include run identifiers, occurrence and definition identifiers, paths, statuses, explicit reasons, stack traces,
redacted configuration, and counters. They never include the full environment.

## Metrics

`Result` publishes occurrence, attempt, passed, failed, skipped, aborted, canceled, and timeout counts. Counters include
container occurrences, so the status counters sum to `occurrenceCount()`. Each logical occurrence is counted once: a
successful retry reports one passed occurrence plus its `attemptCount()`, and the failed steps of earlier attempts remain
visible only as `ATTEMPT` nodes — a passing run never reports failed occurrences because it retried.

## Custom listeners

Implement `Listener` and pass it to `Runner.builder().listener(...)`. Delivery is asynchronous on one dispatcher thread,
bounded, and isolated: a listener exception is recorded as a diagnostic and never changes a task status.
