# Update System Spec

Purpose: synchronize the system specification under `.specify/specs/system/` to reflect recent code changes.

This command replaces the former OpenCode command `.opencode/commands/update-spec.md`.

## Steps

1. Identify which spec documents are stale relative to the current code.
2. Update only the sections that changed; preserve unrelated content.
3. Ensure `AGENTS.md` remains accurate (or migrate it into `.specify/specs/system/` if that becomes canonical).
4. Produce a short report of what sections were updated and why.

## TODO

- Decide whether to keep a system spec as multiple files (current) or consolidate into a single Spec-Kit `spec.md` format.
