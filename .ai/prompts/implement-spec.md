Implement the implementation specification exactly.

## Workflow

1. **Write reproduction tests first** — Before any implementation code, write the test(s) described in the specification that reproduce the bug or demonstrate the missing feature.
2. **Run the reproduction tests** — Execute them to confirm they fail (or otherwise demonstrate the issue) against the current code. Use the project's test commands (see repository guidance files like `AGENTS.md` for the correct verification commands).
3. **Implement the fix/feature** — Make the smallest focused change that satisfies the specification.
4. **Run the reproduction tests again** — Confirm they pass after the implementation.
5. **Run the full relevant test suite** — Execute the project's standard validation (formatting, static analysis, all relevant tests) to ensure no regressions.
6. **Summarize** — Report changed files, validation commands run, results, and any remaining risks.

## Rules

- Do not introduce unrelated refactors.
- Do not change public API beyond what the spec says.
- Keep changes minimal and focused.
- Always write/update tests as described in the specification.
- Let failing tests drive the implementation (test-first / red-green).
- Run the relevant test commands from the project's guidance before and after implementation.
- Run `spotless:apply` (or equivalent formatter) before any build/validation command, per project conventions.
- Summarize changed files, validation commands run, results, and any remaining risks.
