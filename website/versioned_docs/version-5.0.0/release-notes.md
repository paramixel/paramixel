---
title: Release Notes
description: Paramixel release history and version notes.
---

# Release Notes

## 5.0.0

Paramixel 5.0.0 is a major release with significant API changes from 4.x.

### Highlights

- **Package rename** — `org.paramixel.core` → `org.paramixel.api`
- **Spec API** — Builder pattern replaced with accumulating `Spec` pattern (`of(name)` → `.child(...)` → `.resolve()`). See [Spec](./api/spec) for details.
- **New action types** — `Sequential`, `Instance`, `Static`
- **AnnotationResolver** — Resolve `@Paramixel.Id` methods into `Step` actions
- **Descriptor and Metadata** — Execution state now lives in descriptor metadata, not result objects
- **Removed classes** — `Factory`, `Flow`, `Noop`, `Context`, `Store`
- **Configuration promotion** — `failureOnAbort` (default `true`), `failIfNoTests`, `ansi`, `scheduler.queue.capacity`
- **Report format inference** — Format inferred from `paramixel.report.file` extension (`.json`, `.xml`, `.html`, `.log`/`.txt`)
- **Retry and CleanUp** — Moved to `org.paramixel.api.support`

### Migration

See the migration guides:

- [4.x → 5.x](./guides/migration-4-to-5)
- [3.x → 5.x](./guides/migration-3-to-5)
- [2.x → 5.x](./guides/migration-2-to-5)
- [1.x → 5.x](./guides/migration-1-to-5)

## 4.0.0 (Legacy)

Reported issues will be addressed. See the [4.0.0 documentation](/4.0.0/intro) for details.

### Key changes from 3.x

- `Runner` extends `AutoCloseable`
- `Container` replaces `Sequential`, `DependentSequential`, `RandomSequential`, `Lifecycle`
- Executor APIs replaced by scheduler APIs
- `Store` replaces `Attachment`
- Ancestor navigation uses path-based API
- Report format inferred from file extension

## 3.0.1 (Obsolete)

No longer maintained. See the [3.0.1 documentation](/3.0.1/intro) for details.

## 2.0.0 (Obsolete)

No longer maintained. See the [2.0.0 documentation](/2.0.0/intro) for details.

## 1.0.2 (Obsolete)

No longer maintained. See the [1.0.2 documentation](/1.0.2/intro) for details.
