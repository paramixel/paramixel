# Design Interview

Conduct a structured design interview to arrive at a shared,
decision-complete understanding of the design. Walk every design
branch in dependency order. Replace open-ended brainstorming with
a systematic resolution of every open question.

## Objective

Resolve all design decisions before writing a design plan. By the end of
the interview, every branch of the design tree must have an explicit,
confirmed answer. No open questions that affect implementation may remain.

## Input

A problem statement, issue reference, or feature request. Optionally,
paths to existing design documents, specifications, or related issues.

If the input is too vague to proceed (for example, "design a better
system"), ask for a concrete problem statement before continuing.

## Prerequisites

Before beginning the interview:

- Read any existing design documents, specifications, or related issues in
  the repository.
- Inspect the relevant source code and tests to understand the current
  state of the affected components.
- Identify architectural patterns and conventions already in use within the
  project.

## Interview Rules

- Walk each branch of the design tree and resolve dependent decisions one
  by one.
- Include a recommended answer with every question.
- If a question can be answered by inspecting the repository, inspect the
  repository instead of asking.
- Follow the project's coding conventions and guardrails as documented in
  the project's agent instructions.

### Interaction Mode

**If the LLM runtime supports multi-turn conversation:**
Ask exactly one question, then stop and wait for the user's answer before
continuing. Do not include the next question, a checklist of future
questions, or a full interview outline in the same response. After the user
answers, incorporate that answer into the design state and ask exactly one
next dependent question.

**If the LLM runtime does not support multi-turn conversation:**
Produce all questions as a structured list, organized by dependency order,
with a recommended answer for each. Ask the user to respond in bulk. Flag
questions whose answers depend on earlier questions so the user can
resolve them in order.

## Topics to Cover

Address these topics in dependency order. Skip any topic that is not
relevant to the current design.

### Scope and Boundaries

- What problem is being solved? What is explicitly out of scope?
- Which components, modules, or services are affected?

### Data Model

- What data flows through the system? Inputs, outputs, intermediate
  representations.
- What invariants must be preserved?

### API and Contracts

- What interfaces, method signatures, or API endpoints are involved?
- What are the backward-compatibility constraints?

### Error Handling

- What can go wrong? How should each failure mode be handled?
- What exception types and error messages are appropriate?

### Concurrency and Lifecycle

- Are there thread-safety requirements? What is the locking strategy?
- What resource lifecycle must be managed: startup, shutdown, cleanup?

### Testing Strategy

- What test scenarios are required: happy path, edge cases, error conditions?
- Should there be both unit and integration tests?

## Stop Conditions

Abort and report the blocker if:

- The input problem statement is too vague to scope the interview.
- Repository inspection reveals the problem is already solved or is
  infeasible given the current architecture.
- A design branch cannot be resolved without information that is
  unavailable and cannot be inferred from the repository.

Do not invent answers to unresolved design branches. Flag them as
unresolved and explain what information is needed.

## Completion Criteria

The interview is complete when:

- Every branch of the design tree has been resolved with explicit user
  confirmation.
- All interdependent decisions have been addressed in dependency order.
- The resulting design is self-consistent and does not contradict existing
  project conventions.
- No open questions remain that affect implementation.
