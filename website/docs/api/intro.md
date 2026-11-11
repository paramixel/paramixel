---
title: API Overview
description: Overview of Paramixel public packages.
---

# API Overview

Paramixel's public API is under `org.paramixel.api`.

## Main entry points

- `Runner` executes one run: a direct action tree, a selector, or the configured discovery.
- `Launcher` is the command-line `main` for a discovered run.
- `Configuration` provides typed access to properties.
- `Listener` receives discovery, run, and descriptor callbacks.
- `Result` exposes the root `Descriptor` and aggregate outcome.
- `Descriptor` represents one execution occurrence in the tree.
- `Context` provides runtime services to actions (configuration, fixture access).
- `Status` is the outcome value: a `Kind` plus optional message and throwable.
- `Paramixel` contains the discovery annotations.

## Action package

`org.paramixel.api.action` contains `Action` (sealed interface with 8 subtypes: `Step`, `Sequential`, `Parallel`, `Scope`, `Loop`, `Timeout`, `Isolated`, `Conditional`), `ActionBuilder` (sealed interface implemented by every builder and by `Action`), and `Instance`, which is a typed-fixture builder that produces a `Scope`.

## Selector package

`org.paramixel.api.selector` contains `Selector` and its `Builder`: package, class and tag criteria, each with an
`exclude…` twin, composed with `and`.

## Support package

`org.paramixel.api.support` contains `Backoff`, `Retry` and `CleanUp`.

## Exception package

`org.paramixel.api.exception` contains the outcome exceptions (`FailException`, `SkipException`,
`AbortException`) and the framework exceptions (`ConfigurationException`, `ResolverException`).
