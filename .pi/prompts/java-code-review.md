# Java Code Review Playbook

Perform an evidence-backed Java code review focused on correctness, safety,
maintainability, performance, testing, and architecture.

## Objective

Review the supplied Java code and produce prioritized findings with concrete
repository evidence and practical recommendations.

## Input

$ARGUMENTS

Paths to Java source files, tests, packages, modules, issues, or review scope.
If no concrete scope can be identified, ask for one or report a blocker.

## Execution Boundary

This is a read-only review prompt.

- Do not modify code, tests, configs, generated files, or documentation.
- Do not run build, test, formatting, packaging, release, commit, push, network,
  browser, or editor actions unless explicitly requested.
- You may inspect repository files and use read-only search/listing commands.

## Required Inspection

- Read the target source, nearby tests, build/configuration files, and
  repository guidance.
- Identify Java version, frameworks, public API contracts, exception behavior,
  nullability expectations, threading/lifecycle rules, and test conventions.
- Review only what is in scope.

## Finding Requirements

Every finding must include:

- severity: Critical, High, Medium, or Low;
- exact file path and method/class/interface;
- repository evidence;
- impact;
- trigger or example scenario;
- recommended fix;
- suggested test or validation.

Separate confirmed findings from `Needs confirmation`. Avoid speculative
concerns, style-only nitpicks, and novelty suggestions unless they have concrete
maintainability or correctness impact.

## Analysis Checklist

### Correctness and Bugs

Nullability problems, invalid state, broken equality/hashCode, incorrect
collections usage, resource leaks, exception swallowing, boundary errors,
time/date mistakes, concurrency bugs, and API contract violations.

### Security

Input validation, injection risks, unsafe deserialization, path handling,
credential handling, authorization checks, and dependency-risk indicators when
evidence exists in the reviewed code.

### Performance

Algorithmic complexity, unnecessary allocations, inefficient I/O, blocking hot
paths, unbounded memory growth, misuse of streams or parallelism, and caching
risks.

### Maintainability and Readability

High-impact duplication, excessive coupling, unclear names, deep nesting, hidden
side effects, dead code, missing useful documentation, and over-engineering.

### Modern Java Idioms

Recommend modern Java features only when they improve clarity, safety, and
match the repository's configured Java version and style.

### Framework-Specific Concerns

If the repository uses frameworks such as dependency injection, persistence,
serialization, web APIs, or code generation, review lifecycle, configuration,
transaction, serialization, validation, and hidden-behavior risks only when
relevant evidence exists.

### Testing

Missing behavior tests, flaky tests, time-dependent tests, excessive mocking,
poor fixtures, and integration or contract testing gaps.

### Architecture and Design

Layering, boundaries, cohesion, coupling, transaction boundaries, resilience,
shared mutable state, and modularity concerns with concrete impact.

## Output Format

### 1. Executive Summary

Overall assessment, main strengths, and most serious risks.

### 2. Critical and High Findings

Critical and High findings only, or `None`.

### 3. Detailed Findings by Severity

Group findings under Critical, High, Medium, and Low. Use the required finding
fields.

### 4. Needs Confirmation

Suspicious observations that need more evidence, or `None`.

### 5. Suggested Refactorings

Prioritized, incremental refactorings with risk and validation notes, or `None`.

### 6. Positive Observations

Good design decisions, tests, API boundaries, or maintainability choices.

### 7. Overall Code Quality Score

Score from 1 to 10 with a concise justification.

## Stop Conditions

Stop and report a blocker if the review scope cannot be identified, required
files cannot be read, or repository constraints make the requested review
invalid.
