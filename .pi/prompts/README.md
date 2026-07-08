# Java Prompt Templates

Pi-compatible and project-agnostic prompt templates for structured Java software
engineering workflows. Executable prompts are self-contained and use pi's
`$ARGUMENTS` variable for parameterization.

## Prompt Contract

All executable prompts in this directory follow the repository-level
[`PROMPT_CONTRACT.md`](../PROMPT_CONTRACT.md). Review changes with
[`PROMPT_REVIEW_CHECKLIST.md`](../PROMPT_REVIEW_CHECKLIST.md).

Reference documents such as `coding-principles.md` and `planning-workflow.md`
are not standalone executable workflows and may omit `$ARGUMENTS`.

## Prompt Index

| File | Purpose | Use When |
|---|---|---|
| `design-interview.md` | Structured design interview | You have a problem statement and need to resolve design decisions before writing a plan |
| `create-design-plan.md` | Design plan document | You have a resolved design and need to document approach, tradeoffs, and test strategy |
| `create-implementation-spec.md` | Implementation specification | You have a design plan and need step-by-step implementation instructions |
| `implement-spec.md` | Execute an implementation spec | You have a complete implementation specification and are ready to write code |
| `analyze-code.md` | Correctness and bug analysis | You need a focused bug hunt on a module without writing code |
| `java-code-review.md` | Java code review | You need an evidence-backed engineering review of Java code |
| `java-code-coverage.md` | Java coverage improvement | You want one safe, deterministic iteration to improve test coverage |
| `java-performance-review.md` | Java performance review | You have profile, benchmark, or repository evidence and need performance recommendations |
| `fix-code-loop.md` | Single fix loop | You want one bounded analysis-to-implementation loop |
| `fix-code-loop-10.md` | 10-loop fix sequence | You want up to ten bounded fix loops across a project |
| `fix-code-loop-100.md` | 100-loop fix sequence | You want up to one hundred bounded fix loops across a project |
| `coding-principles.md` | Reference: engineering discipline | You need reusable coding-agent behavior guidance |
| `planning-workflow.md` | Reference: plan naming | You need durable plan-file naming guidance |

## Workflow Sequence

The primary design-to-implementation workflow:

```text
Problem Statement
      │
      ▼
design-interview.md ──► Resolve design decisions
      │
      ▼
create-design-plan.md ──► Document approach, tradeoffs, and test strategy
      │
      ▼
create-implementation-spec.md ──► Define reproduction-first implementation steps
      │
      ▼
implement-spec.md ──► Make the scoped code/test/documentation changes
      │
      ▼
analyze-code.md ──► Optional evidence-backed correctness review
```

Standalone prompts and references:

```text
java-code-review.md ──► Java code review
java-code-coverage.md ──► Java coverage improvement
java-performance-review.md ──► Java performance review
fix-code-loop.md ──► Single bounded analysis-to-implementation loop
fix-code-loop-10.md ──► Up to ten bounded fix loops
fix-code-loop-100.md ──► Up to one hundred bounded fix loops
planning-workflow.md ──► Reference for plan file naming
coding-principles.md ──► Engineering discipline reference
```

## Template Structure

Prompts use stable sections appropriate to their type:

- **Read-only analysis**: Objective → Input → Execution Boundary → Required
  Inspection → Evidence Rules → Stop Conditions → Output Format → Completion
  Criteria.
- **Artifact-writing planning**: Execution Boundary → Objective → Input →
  Output Path Rules → Required Inspection → Stop Conditions → Deliverables →
  Completion Boundary → Final Response.
- **Implementation**: Objective → Input → Preflight → Stop Conditions →
  Workflow → Change Discipline → Validation Discipline → Error Recovery →
  Acceptance Criteria → Final Response.
- **Orchestration**: Execution Boundary → Objective → Input → Loop State →
  Issue Selection → Phase Contracts → Handoffs → Stop Conditions → Final Report.

## Design Principles

- **pi-native**: Executable prompts use `$ARGUMENTS` as the only required
  substitution variable.
- **LLM/provider agnostic**: Prompts do not require a specific model family,
  hosted service, editor, browser, shell, or IDE.
- **Project-agnostic**: Commands and conventions are discovered from the target
  repository when possible. Generic command examples are illustrative defaults.
- **Deterministic**: Prompts define stable sections, stop conditions, evidence
  requirements, and final responses.
- **Bounded**: Prompts state whether they are read-only, artifact-writing, or
  implementation-capable.

## License

[MIT](../LICENSE)

---

Copyright (c) 2026-present Douglas Hoard
