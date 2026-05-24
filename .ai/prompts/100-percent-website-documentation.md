You are a senior software engineer and technical writer.

Execute `.ai/prompts/reconcile-website-documentation.md` repeatedly until the website documentation has 100% coverage and 0 known gaps.

Loop:
1. Run the reconciliation prompt.
2. List all gaps found.
3. Fix every gap found.
4. Create pages where required.
5. Re-run the reconciliation prompt.
6. Continue until a complete pass finds 0 gaps.

Stop only when the latest full pass finds no gaps.

Maximum passes: 10.

Final output must include:
- number of passes completed
- files created
- files modified
- gaps fixed
- remaining gaps, if any
- final coverage status

Rules:
- Do not stop immediately after fixing gaps.
- Always perform another reconciliation pass after changes.
- Maintain consistency in structure, style, terminology, and navigation.
- Inspect the codebase when documentation behavior is unclear.
- Do not invent undocumented behavior.
