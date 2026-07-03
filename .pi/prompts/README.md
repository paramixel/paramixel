# Prompt Templates

LLM-agnostic and project-agnostic prompt templates for structured software engineering
workflows. Every prompt is self-contained — drop it into any LLM runtime or coding agent
and it works without additional context.

Language-specific prompts (Java) are marked below.

## Prompt Index

| File | Purpose | Use When |
|---|---|---|
| `planning-workflow.md` | Plan file naming convention | You need a durable plan file and want consistent naming |
| `design-interview.md` | Structured design interview | You have a problem statement and need to resolve all design decisions before writing a plan |
| `create-design-plan.md` | Design plan document | You have a resolved design and need to document the approach, tradeoffs, and test strategy |
| `create-implementation-spec.md` | Implementation specification | You have a design plan and need step-by-step implementation instructions |
| `implement-spec.md` | Execute an implementation spec | You have a complete implementation specification and are ready to write code |
| `analyze-code.md` | Correctness and bug analysis | You need a focused bug-hunt on a module without writing any code |
| `fix-code-loop.md` | Full analysis-to-implementation loop | You want to find and fix correctness issues in one end-to-end pass |
| `java-code-review.md` | Java code review (`{java}` specific) | You need a comprehensive engineering review of Java code |
| `java-code-coverage.md` | Java coverage improvement (`{java}` specific) | You want one safe, deterministic iteration to improve test coverage |
| `java-performance-review.md` | Java performance review (`{java}` specific) | You have profile or benchmark data and need performance recommendations |
| `website-docs-reconciliation.md` | Docs reconciliation with source | You need to update documentation to match current implementation |

## Workflow Sequence

The primary design-to-implementation workflow:

```
Problem Statement
      │
      ▼
design-interview.md ──► Resolve all design decisions
      │
      ▼
create-design-plan.md ──► Document approach, tradeoffs, test strategy
      │
      ▼
create-implementation-spec.md ──► Step-by-step implementation instructions
      │
      ▼
implement-spec.md ──► Write the code
      │
      ▼
analyze-code.md ──► Verify correctness (optional review pass)
```

Standalone prompts (use independently):

```
java-code-review.md ──► Comprehensive Java code review (any time)
java-code-coverage.md ──► One focused coverage improvement iteration
java-performance-review.md ──► Performance analysis from profile data
website-docs-reconciliation.md ─► Docs audit against source code
planning-workflow.md ──► Reference for plan file naming (not an executable prompt)
```

## Template Structure

Prompts follow a consistent pattern tailored to their purpose:

- **Design workflow prompts** (`design-interview.md`, `create-design-plan.md`,
  `create-implementation-spec.md`, `implement-spec.md`, `analyze-code.md`)
  follow: Objective → Input → Prerequisites → Stop Conditions →
  Deliverables → Completion Criteria.

- **Review and analysis prompts** (`java-code-review.md`,
  `java-code-coverage.md`, `java-performance-review.md`) use
  purpose-specific structures with priorities, checklists, and output formats.

- **Task-specific prompts** (`website-docs-reconciliation.md`) follow the
  task's natural workflow.

## Design Principles

- **LLM-agnostic**: No model names, provider names, or LLM-specific capabilities.
  The `design-interview.md` prompt handles both multi-turn and single-turn runtimes
  via branching instructions.
- **Project-agnostic**: No project names, module paths, or tool-specific commands.
  References to build commands, test commands, or conventions use generic placeholders
  (e.g., "the project's agent instructions").
- **Tooling-agnostic**: No editor commands, file-system verbs like "open an editor,"
  or IDE features.
- **Self-contained**: Every prompt contains all the context it needs to be used.
  Drop it into any LLM runtime and it works.
