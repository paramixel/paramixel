---
title: Exceptions
description: Framework exceptions and the signals that shape task status.
---

# Exceptions

All framework exceptions extend `ParamixelException`.

| Exception | Meaning |
| --- | --- |
| `ConfigurationException` | Invalid or unloadable configuration; thrown before any test code runs |
| `CompilationException` | Invalid definition or plan; carries the collected diagnostics |
| `ResolverException` | Invalid or uninvocable discovery factory |
| `ResourceException` | A required `Key` resource is not available; names the key and occurrence path |
| `FailException` | Author-signaled failure (`FAILED`) |
| `SkipException` | Author-signaled skip (`SKIPPED`) |
| `AbortException` | Author-signaled assumption abort (`ABORTED`) |

## Status mapping

Any other throwable from a callback is recorded as `FAILED` with its original cause. Cancellation-driven interruption is
recorded as `CANCELED`; a timeout expiry is `FAILED` with an explicit timeout reason. Unrecoverable VM errors
(`VirtualMachineError` other than `StackOverflowError`) are recorded, abort the run, and are rethrown to the caller.

## Framework versus test failures

`Result.failures()` carries run-level infrastructure failures, and `Result.diagnostics()` carries warnings and listener
failures. An infrastructure defect is never reported as a passing test, even when both cases exit non-zero.
