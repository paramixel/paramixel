---
title: API Overview
description: Overview of Paramixel public packages.
---

# API Overview

Paramixel's public API is under `org.paramixel.api`.

## Main entry points

- `Runner` executes actions, selectors, or classpath-discovered factories.
- `Configuration` provides typed access to properties.
- `Listener` receives discovery, run, and descriptor callbacks.
- `Result` exposes the root `Descriptor` and aggregate outcome.
- `Descriptor` represents one execution occurrence in the tree.
- `Context` provides runtime services to actions (configuration, fixture access).
- `Status` represents execution state with severity aggregation.
- `Paramixel` contains annotations used by discovery and annotation resolution.

## Action package

`org.paramixel.api.action` contains `Action` (sealed interface with 12 subtypes), `Builder` (sealed interface with 9 subtypes), and built-in action types: `Step`, `Assert`, `Sequence`, `Parallel`, `Scope`, `Static`, `Instance`, `Delay`, `Repeat`, `Timeout`, `Conditional`, `Isolated`, and the `Each` data-driven utility.

## Selector package

`org.paramixel.api.selector` contains discovery selectors for package, class, tag, and boolean composition.

## Support package

`org.paramixel.api.support` contains `Retry` and `CleanUp` utilities.

## Exception package

`org.paramixel.api.exception` contains framework and outcome exceptions.
