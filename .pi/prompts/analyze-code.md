# Code Analysis

Analyze the target package or class for correctness issues and potential bugs.
Produce findings only. Do not write or modify code.

## Objective

Identify runtime bugs, logic errors, race conditions, resource leaks,
exception handling problems, API contract violations, and deterministic
behavior risks in the target package or class. Report only evidence-backed findings
with location, impact, and suggested validation.

## Input

$ARGUMENTS

Paths to source files, interfaces, tests, build configuration, repository
guidance, or an issue reference for the target package or class.

If no target can be identified from the input or repository, ask for the target
or report a blocker before continuing.

## Execution Boundary

This is a read-only analysis prompt.

- Do not write code.
- Do not modify tests, source files, build files, configs, generated files, or
  documentation.
- Do not run build, test, formatting, packaging, release, commit, push, network,
  browser, or editor actions unless the user explicitly requests read-only
  validation commands.
- You may inspect the repository by reading files and using read-only search or
  listing commands.

## Required Repository Inspection

Before performing analysis:

- Read every relevant source file, interface, test file, and build or
  configuration file for the target package or class.
- Read repository guidance that defines coding, testing, validation, or agent
  behavior.
- Identify documented contracts, public API semantics, exception contracts, and
  compatibility constraints.
- Understand the thread, executor, synchronization, resource, and lifecycle model where relevant.
- Identify existing test patterns and validation commands, but do not run them
  unless explicitly permitted by the input.

Proceed only after the affected code and contracts are understood.

## Evidence Rules

Every confirmed finding must include:

- Severity: Critical, High, Medium, or Low, unless project guidance defines a
  different severity scale.
- Exact file path and method, class, or interface.
- Repository evidence supporting the finding.
- Why it is a correctness issue rather than a style preference.
- Minimal trigger scenario.
- Observable impact.
- Suggested fix.
- Suggested test or validation.

Use `Needs confirmation` for suspicious observations that lack enough evidence.
Do not present speculation as a confirmed bug.

## Stop Conditions

Abort and report the blocker if:

- The target package or class cannot be located.
- Required files cannot be read.
- The codebase cannot be understood sufficiently to identify contracts and
  semantics.
- The issue depends on product decisions not present in the repository.
- Repository evidence shows the reported problem is already solved.

## Output Format

1. **Summary**
   - Most serious confirmed issues, or `None`.
2. **Detailed Findings**
   - Group by severity: Critical, High, Medium, Low.
   - For each finding include the required evidence fields.
3. **Needs Confirmation**
   - Suspicious observations that require more evidence, or `None`.
4. **Scope Inspected**
   - Files and repository guidance inspected.
5. **Validation Not Run**
   - Commands identified but not run, or `Not applicable`.

## Completion Criteria

The analysis is complete when every relevant source file, interface, and test in
the target scope has been inspected and every confirmed finding is tied to
specific repository evidence and observable incorrect behavior.
