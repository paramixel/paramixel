# Java Performance Review Playbook

Review the provided code, profile data, or performance concern and
recommend optimizations that preserve behavior and improve measurable
outcomes.

## Objective

Identify performance issues with credible impact, recommend specific
changes, and flag any correctness or maintainability risks those changes
introduce. Do not optimize without evidence.

## Input

The code, profile results, benchmark output, or performance concern to
review.

## Priorities

1. Correct code first.
2. Clean, maintainable code second.
3. Fast code third.

## Rules

- Do not change behavior unless explicitly asked.
- Identify performance improvements only when justified by code, benchmarks,
  profiling data, workload details, or clear hot-path reasoning.
- Prefer simple, readable optimizations over clever or fragile ones.
- Call out any optimization that could reduce readability, safety, or
  maintainability.
- Avoid premature optimization. Ask for benchmarks, profiling data, workload
  details, or hot paths when needed.
- Do not rewrite large sections unless there is a clear benefit.
- Preserve the project's target language version compatibility and public
  APIs unless there is a strong reason not to.
- Prefer standard JDK APIs unless a dependency is already present.
- Consider CPU, memory allocation, GC pressure, I/O, concurrency, database
  access, caching, and algorithmic complexity.
- Flag thread-safety or lifecycle risks.
- Follow the language idiom guardrails in the project's agent instructions.

## Output Format

For each review, provide the following sections.

### 1. Summary of Likely Performance Issues

A concise list of the most impactful performance concerns found.

### 2. Correctness Risks

Any correctness risks introduced by each proposed change. If a change
introduces no correctness risk, state that explicitly.

### 3. Readability and Maintainability Impact

How each change affects code clarity and maintainability.

### 4. Recommended Changes

For each recommendation:

- Before and after code when useful.
- Why the change is faster or more efficient.
- Tradeoffs, explained clearly.
- Confidence level: High, Medium, or Low.

### 5. Benchmarking or Profiling Suggestions

How to measure the impact of the recommended changes. Specific commands or
approaches to use.

### 6. No-Change Cases

Explicitly state when no change is recommended and why.
