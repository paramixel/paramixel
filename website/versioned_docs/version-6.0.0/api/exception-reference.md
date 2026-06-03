---
title: Exception Reference
description: Outcome and framework exceptions.
---

# Exception Reference

Paramixel maps specific exceptions to action outcomes.

## Outcome exceptions

- `FailException` communicates a failed outcome.
- `SkipException` communicates a skipped outcome.
- `AbortedException` communicates an aborted outcome.

Thrown exceptions may also be converted to failed statuses by framework execution.

## Framework exceptions

- `ConfigurationException` for invalid configuration or loading failures.
- `CycleDetectedException` for cyclic action graphs.
- `ResolverException` for discovery or annotation resolution failures.
- `PolicyException` for invalid retry or policy configuration.

Use outcome exceptions to signal deliberate non-pass outcomes from step logic. The framework converts them to the corresponding status for listeners and reporting. A step that completes without throwing is recorded as passed.
