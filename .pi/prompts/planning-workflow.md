# Planning Workflow

A naming convention for durable plan files. Use this convention whenever a
task calls for a plan document.

## Plan File Naming Convention

All plans are written to `.pi/plans/` with this naming convention:

```
<action>-<number>-<description>.md
```

### Components

- **action**: `fix`, `feature`, `refactor`, `chore`, or `polish`
  (matches conventional commit prefixes).
- **number**: issue or ticket number. Omit for ad-hoc tasks.
- **description**: brief lowercase summary with words separated by dashes.

### Examples

```
.pi/plans/fix-0123-null-pointer-in-user-lookup.md
.pi/plans/feature-0456-add-retry-with-backoff.md
.pi/plans/refactor-simplify-error-propagation.md
```

### Commit Prefix Reference

If the project uses conventional commits, align plan actions with the
project's commit prefix conventions:

| Action | Commit Prefix |
| --- | --- |
| fix | `fix:` |
| feature | `feature:` |
| refactor | `refactor:` |
| chore | `chore:` |
| polish | `polish:` |

Dependency updates use a scoped prefix: `chore(deps):` or `fix(deps):`.
