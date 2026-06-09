---
title: Introduction
description: What Paramixel is and how its execution-tree model works.
---

# Introduction

Paramixel builds tests from execution trees. A test declares a tree of `Action` instances built through `Builder` subtypes or static factories. The `Runner` resolves the tree, executes it, and returns a `Result` with a descriptor tree and aggregate status.

The result tree mirrors the action tree, so the same structure appears in code, execution, reports, and programmatic inspection.

## Core model

- An `Action` is an immutable execution unit with a `displayName()`.
- A `Builder` is a mutable builder that produces an immutable `Action` via `build()`.
- A `Descriptor` represents one execution occurrence of an action.
- A `Status` is one of `PENDING`, `RUNNING`, `PASSED`, `FAILED`, `SKIPPED`, or `ABORTED`.
- Discovery finds public static factory methods annotated with `@Paramixel.Factory`.

## When to use Paramixel

Use Paramixel when a test benefits from explicit composition: setup/body/teardown lifecycles, parallel branches, fixture-scoped steps, tag-based selection, and reports that mirror the execution tree.

## How Paramixel complements JUnit

Paramixel is not a replacement for JUnit. They can coexist in the same codebase and build: keep ordinary unit tests and method-level parameterized tests in JUnit, and use Paramixel for tree-shaped tests whose structure matters.

In short:

- JUnit parameterized tests parameterize test invocations.
- Paramixel composes and schedules execution trees.

That distinction matters when a test needs global setup/teardown, nested sequential and parallel branches, generated environments, per-branch lifecycle, retries, timeouts, isolation, and structural reports.

## Configuration model

Paramixel uses property-based configuration. Put defaults in `paramixel.properties`, pass JVM system properties with `-D`, or construct programmatic `Configuration` objects for custom runners.

## Learn next

- [When to Use Paramixel](when-to-use)
- [Quick Start: Your First Execution Tree](first-test)
- [Core Concepts](../core-concepts)
- [Test Shapes](../test-shapes)
