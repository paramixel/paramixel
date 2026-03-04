# Plan

## Goal

Adopt GitHub Spec-Kit structure for this repository without changing Paramixel behavior.

## Approach

1. Treat `.specify/specs/system/` as the canonical home of the Paramixel system specification.
2. Keep the existing spec documents intact (moved, not rewritten) to preserve all functional requirements.
3. Provide Spec-Kit command templates and custom commands that map the prior OpenCode command inventory.
4. Keep CI unchanged (`./mvnw -B clean verify`).

## Verification

- Run `./mvnw verify` to ensure no behavior changes were introduced by the migration.
- Use the custom conformance checklist command under `.specify/commands/` to manually audit spec alignment.

## TODO

- Run `specify init` to generate any missing Spec-Kit scripts/templates, then reconcile with project-specific templates.
