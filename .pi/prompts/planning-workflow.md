# Planning Workflow

Reference naming convention for durable plan files. This is not an executable
workflow prompt and intentionally omits `$ARGUMENTS`.

## Plan File Location

Plans are written to `.pi/plans/`. The repository `.gitignore` should ignore
`.pi/plans/` so local planning artifacts are not committed accidentally.

## Plan File Naming Convention

Use this naming convention:

```text
<action>[-<number>]-<description>.md
```

### Components

- **action**: `fix`, `feature`, `refactor`, `chore`, or `polish`.
- **number**: issue or ticket number, if available.
- **description**: brief lowercase summary with words separated by dashes.

### Examples

```text
.pi/plans/fix-0123-null-pointer-in-user-lookup.md
.pi/plans/feature-0456-add-retry-with-backoff.md
.pi/plans/refactor-simplify-error-propagation.md
.pi/plans/chore-harden-prompt-determinism.md
```

## Overwrite Rule

Do not overwrite an existing plan unless the user explicitly requested
replacement. If a derived path exists, choose a more specific non-conflicting
name or report a blocker.

## Commit Prefix Reference

If the project uses conventional commits, align plan actions with project commit
prefixes:

| Action | Commit Prefix |
| --- | --- |
| fix | `fix:` |
| feature | `feature:` |
| refactor | `refactor:` |
| chore | `chore:` |
| polish | `polish:` |

Dependency updates use scoped prefixes such as `chore(deps):` or `fix(deps):`.
