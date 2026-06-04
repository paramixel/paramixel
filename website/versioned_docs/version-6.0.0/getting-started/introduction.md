---
title: Introduction
description: What Paramixel is and how its action-tree model works.
---

# Introduction

Paramixel builds tests from action trees. Instead of hiding execution in a method runner, a test declares a tree of `Action` instances built through `Builder` subtypes. The `Runner` resolves the tree, executes it, and returns a `Result` with a descriptor tree and aggregate status.

## Core model

- An `Action` is an immutable execution unit with a `displayName()`. The sealed interface has 10 concrete subtypes.
- A `Builder` is a mutable builder that produces an `Action` via `build()`. Seven subtypes correspond to configurable action types.
- A `Descriptor` represents one execution occurrence of an action.
- A `Status` is one of `PENDING`, `RUNNING`, `PASSED`, `FAILED`, `SKIPPED`, or `ABORTED`.
- Discovery finds public static factory methods annotated with `@Paramixel.Factory`.

## When to use Paramixel

Use Paramixel when a test benefits from explicit composition: setup/body/teardown lifecycles, parallel branches, fixture-scoped steps, tag-based selection, and reports that mirror the execution tree.

## Configuration model

Paramixel uses property-based configuration. Put defaults in `paramixel.properties`, pass JVM system properties with `-D`, or construct programmatic `Configuration` objects for custom runners.
