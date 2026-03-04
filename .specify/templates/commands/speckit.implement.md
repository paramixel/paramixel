---
description: Implement a change while preserving the Paramixel system specification
---

Implement the requested change.

Before writing any code:

1. Read `.specify/specs/system/00-overview.md` to orient yourself.
2. Read `.specify/specs/system/04-modules.md` to identify which module(s) are affected.
3. Read `.specify/specs/system/05-conventions.md` to understand naming, packaging, and structural rules.
4. Read `.specify/specs/system/03-api-contracts.md` if the change touches any API surface.
5. Read `.specify/specs/system/02-data-model.md` if the change touches any API model types or engine internal data objects.

Then implement the change:

- Follow ALL conventions in `.specify/specs/system/05-conventions.md`.
- Do not add Maven dependencies without explicit user approval.
- Write unit and functional tests per `.specify/specs/system/06-testing.md`.
- Run `./mvnw verify -pl <affected-module(s)>` and fix all failures.
- If the API model changed, update `.specify/specs/system/02-data-model.md`.
- If an API contract changed, update `.specify/specs/system/03-api-contracts.md`.

After implementation:

- Report what changed, what tests were added, and any outstanding concerns.
