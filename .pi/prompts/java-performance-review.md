# Java Performance Review Playbook

Review performance risks and optimization opportunities using repository,
benchmark, profile, or workload evidence.

## Objective

Identify actionable performance improvements while preserving correctness,
maintainability, public API behavior, and repository conventions.

## Input

$ARGUMENTS

Paths to source files, profiles, benchmarks, traces, workload descriptions,
issues, or performance observations. If evidence is missing, separate
hypotheses from confirmed findings.

## Execution Boundary

This is a read-only review prompt unless the user explicitly requests changes.

- Do not modify code, tests, configs, generated files, or documentation.
- Do not run benchmarks or profilers unless explicitly requested.
- Do not require browser/editor actions, network access, dependency changes,
  commits, or pushes.
- You may inspect repository files and provided performance artifacts.

## Required Inspection

- Read target source, tests, repository guidance, and provided performance data.
- Identify hot paths, allocation or I/O patterns, concurrency/lifecycle behavior,
  and existing benchmark coverage.
- Distinguish measured bottlenecks from plausible hypotheses.

## Priorities

Evaluate in this order:

1. Correctness-preserving performance fixes supported by evidence.
2. Algorithmic complexity and unnecessary work in hot paths.
3. Resource leaks, unbounded memory growth, blocking, or contention.
4. Allocation, I/O, parsing, serialization, or caching inefficiencies.
5. Benchmarking or profiling gaps needed before changing code.

## Rules

- Do not recommend broad rewrites without evidence.
- Include correctness and maintainability risks for each recommendation.
- Prefer simple changes that can be validated with repository-discovered
  benchmarks or tests.
- Mark speculative optimizations as hypotheses.

## Output Format

### 1. Summary

Confirmed issues and highest-value opportunities, or `None`.

### 2. Evidence Reviewed

Files, benchmarks, profiles, traces, or workload descriptions inspected.

### 3. Confirmed Performance Findings

For each finding include path, symbol, evidence, impact, recommendation,
correctness risk, maintainability risk, and validation.

### 4. Hypotheses Needing Measurement

Potential issues that require profiling or benchmarks, or `None`.

### 5. Recommended Validation

Repository-discovered or suggested benchmark/profile/test commands. Mark generic
commands as examples only.

### 6. No-Change Cases

Areas reviewed where no change is recommended and why.

## Stop Conditions

Stop and report a blocker if:

- The review scope cannot be identified.
- Required files or provided performance artifacts cannot be read.
- Repository constraints conflict with the requested review.
- Evidence is insufficient for confirmed findings; place remaining observations
  under hypotheses instead of presenting them as facts.
