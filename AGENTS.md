# AGENTS.md

Instructions for coding agents working with this project, whether it is a Git checkout, another VCS workspace, or an unversioned directory. These instructions are language-, platform-, and VCS-agnostic. Discover the actual project contracts before planning, editing, or running code.

## 1. Instruction precedence and scope

- Follow the user's current request and applicable higher-priority instructions.
- Read this file and any `AGENTS.md` files governing the paths involved. A more specific
  `AGENTS.md` applies within its directory; do not assume it governs unrelated directories.
- Treat `PROJECT.md` (when present), the repository's specifications, architecture docs, build
  configuration, tests, and existing source as the authorities for **project-specific facts**. Planning documents record decisions but
  do not prove that the current implementation matches them.
- Use `DESIGN.md` and `IMPLEMENT.md` for the workflows described below.
  They are templates, not additional permission to change code.
- If instructions or sources conflict, identify the conflict. Do not silently invent a resolution
  to a behavior-defining or destructive conflict.

## 2. Establish repository context first

Before making nontrivial changes:

1. Determine the workspace root, available files, and whether version control exists. Preserve all pre-existing files and user edits. Use VCS status/history only if actually available; otherwise establish a baseline with file inventory, timestamps or content hashes as appropriate.
2. Identify the language(s), dependency managers, build system, supported platforms, test framework,
   formatter/linter, generated sources, and CI gates **from this project directory**.
3. Read relevant entry points, public interfaces, tests, documentation, and local conventions.
4. Identify the owners of the affected components and the direction of their dependencies.
5. Identify relevant project contracts: compatibility, schemas, protocols, data formats, persistence,
   security, concurrency, error handling, performance budgets, and user-visible behavior.
6. Record unknowns explicitly. A missing command, spec, or test is **unknown**, not evidence that no
   constraint exists. Do not guess paths or commands from another repository.

### Project instructions and project memory

- Read `PROJECT.md` when present for project-specific requirements, conventions, and constraints.
- Recall accumulated knowledge through the memory tools the session advertises, when it advertises
  them: `mcp__memory__search_nodes` for a topic before re-deriving a decision or a trap, and
  `mcp__memory__open_nodes` or `mcp__memory__read_graph` when the topic is unclear and a wider read
  is worth the context.
- Treat what memory returns as historical context, not authoritative project instructions. Verify
  important facts against the current source and project documentation.
- Do not assume the memory server is configured, running, or reachable. Without its tools, proceed
  without memory; nothing in the project depends on the graph being available.
- Record durable knowledge as you work; section 10 defines what belongs in the graph.

Do not initialize Git, add a VCS, or require a commit merely to complete a task. An absent VCS is normal, not a blocker.

Keep discovery proportional to the request. Use targeted inspection first and expand only when the
change crosses boundaries. Do not treat historical plans or comments as proof of current behavior.

## 3. Task modes and authorization

Interpret the user's request according to the requested outcome:

| Mode | Deliverable | Permitted work |
| --- | --- | --- |
| Research / review | Findings and evidence | Inspect; do not edit production code |
| Design planning | `plans/<slug>.design.md` | Investigate and propose; do not implement |
| Implementation planning | `plans/<slug>.impl.md` | Plan concrete changes and verification; do not implement |
| Implementation | Code, tests, docs, and verification record | Execute changes within the user's authorization |

- **Plans are not execution authorization.** Approval of a design or plan establishes an agreed
  approach; editing code still requires a user request that authorizes implementation. A direct
  request to implement or fix something is such authorization within its stated scope.
- For substantial changes or changes to contracts, interfaces, persistent data, or architecture,
  prefer a design plan followed by an implementation plan when the user requests planning or the project requires it. Request a decision if a behavior-defining
  question remains unresolved and cannot be handled safely within the requested scope.
- A small, well-bounded fix may be implemented directly when the user requests a fix; a separate
  design plan is not mandatory. If planning is requested, produce the requested plan rather than
  changing code.
- Use `plans/<descriptive-kebab-case-name>.design.md` and
  `plans/<descriptive-kebab-case-name>.impl.md` when the project does not prescribe another
  location. This is a filesystem convention, not a Git requirement. Respect existing ignore/exclusion
  rules if a VCS exists; do not modify them merely to make plans visible.
- The implementation plan references the exact design it implements and its decision state. If a
  separate design is unnecessary, record the reason. Never fabricate approval. A formal approval
  step is required only when requested or defined by the project workflow; otherwise label decisions
  as proposed/accepted by explicit request and distinguish planning authorization from execution.
- Material deviations from a governing design require documenting the changed decision and securing
  a user decision when the change exceeds the authorized scope or changes an externally visible contract. Minor implementation details within the governing design may be
  recorded as execution deviations.

## 4. Engineering standards

- Make the smallest **coherent** change that satisfies the requested behavior and preserves
  relevant existing contracts. Avoid unrelated refactoring and speculative frameworks.
- Follow established repository conventions for naming, layout, architecture, API shape, error
  handling, documentation, dependencies, and formatting. Do not impose a preferred stack.
- Deliver complete production-quality changes: no placeholders, fake implementations, abandoned
  debug code, dead scaffolding, or unresolved TODOs introduced for promised functionality.
- Reuse existing abstractions when appropriate; justify added dependencies or new public surfaces.
- Explicitly handle meaningful failure paths, invalid input, resource cleanup, state transitions,
  boundary conditions, concurrency, and recovery when they are in scope.
- Preserve public compatibility, data integrity, migrations, and reproducibility where the project
  requires them. Make deliberate contract changes explicit and update all affected consumers.
- Treat tests and safety checks as contracts: never delete, weaken, or bypass them merely to make
  a change appear successful.
- Do not edit build output or generated files directly when their source-of-truth generator exists;
  update the source and regenerate using the project's supported process when authorized.

## 5. Tests, evidence, and completion

For implementation work:

1. Establish the baseline: record existing failures, pre-existing files/edits, and relevant reproduction steps. When no VCS exists, use a scoped inventory or content hashes as needed rather than claiming to have a diff.
2. Add or update the most focused deterministic regression that proves the intended behavior.
   For a defect, prefer a test demonstrating the old failure before fixing it when feasible.
3. Cover preserved behavior and relevant negative, boundary, failure, and concurrency cases.
4. Use owned temporary test state. Avoid modifying real user data, production services, global
   caches, credentials, or external accounts. Do not add network-dependent tests without explicit
   project justification and authorization for the external effects.
5. Discover the actual build/test/lint commands from project configuration and CI; run focused
   checks first and then the applicable broader gates. Do not invent commands or claim a check ran
   when it did not.
6. Review every changed file against the recorded baseline. Run project-supported formatting and static checks. If Git is present, inspect `git diff`, status, and optionally `git diff --check`; otherwise inspect the actual modified files and keep an explicit change inventory.
7. Update authoritative documentation and fixtures whenever behavior or contracts change.

Completion must be **evidence-based**, not a percentage-of-confidence claim. Report:

- What actually changed, including affected files and user-visible behavior.
- Exact checks performed and their observed results, distinguishing new failures from baseline.
- Checks not run, remaining gaps, risks, and why they remain.
- Any departure from the governing plan and the resulting decision or justification.

Do not call work fully verified while known required gates are failing or unrun. If verification is
blocked, report the work as partial/blocked rather than assuming it works.

## 6. Safe repository and tool behavior

- Preserve user edits, untracked files, configuration, and test artifacts not owned by this task.
- Do not discard or overwrite unrelated changes. Never use destructive reset/clean operations
  without explicit authorization and an understood scope.
- Do not initialize version control, stage, commit, push, publish, deploy, install, or change external infrastructure unless explicitly requested. If commits are explicitly required but no VCS exists, report the limitation; do not silently create a repository or fabricate commit IDs. Local tests and local builds are allowed during authorized implementation,
  subject to project guidance and environment restrictions.
- Do not expose secrets or copy real credentials into source, plans, logs, or examples.
- Before executing commands with side effects outside the working tree, assess the targets and ask
  for authorization when not already covered by the request.
- Make no claim of sandboxing, safety, determinism, or compatibility without supporting evidence.

## 7. Planning and handoff contract

**Design plan** (`DESIGN.md`) defines the problem, observed baseline, requirements,
constraints, proposed behavior, decisions/trade-offs, relevant edge cases, acceptance criteria, and
open questions. It should not prescribe a file-by-file work schedule or record execution results.

**Implementation plan** (`IMPLEMENT.md`) translates the governing design—or an explicitly
justified small change—into ordered, concrete modifications, regression coverage, commands, gates,
rollback considerations, and an execution record. It must not silently redefine design decisions.

For both plan types:

- Replace placeholders with inspected facts; cite files, symbols, tests, commands, or docs.
- Separate **observed**, **proposed**, **assumed**, and **not verified** information.
- State non-applicability with a reason rather than fabricating work.
- Keep plans proportionate to complexity while retaining the mandatory outcomes and evidence.
- Mark statuses accurately. A created plan starts as a draft unless approval is explicitly known.
- When implementation is authorized and a plan exists, update the implementation plan's execution record with actual
  results, deviations, blockers, and remaining work; never backfill anticipated results as facts.

## 8. Optional version control and change tracking

- Detect Git, another VCS, or no VCS; never assume the project has history or a clean checkout.
- Without VCS, protect original content, record exact modified paths, and use copies, hashes,
  snapshots, or project-native backups **only when needed and authorized**. Avoid copying secrets
  or large data directories to create indiscriminate backups.
- A user-requested commit-per-step workflow is conditional on an existing usable VCS. If absent,
  perform work only to the degree still authorized and possible; report each completed step with
  verification evidence and a change inventory, and clearly state that commits were impossible.
- Never equate the absence of a VCS diff with a clean or unmodified workspace.

## 9. Final response checklist

Before reporting completion, confirm that the response distinguishes design from implementation,
changes from proposals, and tests run from tests merely planned. Surface unresolved decisions,
contract risks, and anything the user must authorize before further action.


## 10. Persistent project memory

Project memory is a knowledge graph served by the `memory` server entry, which this machine
declares in its user layer as `<user.home>/.local/share/amanda/mcps/memory.json`. There is no markdown memory
file to read or maintain and no per-topic set of files to update. Memory is project-scoped: the server
uses the full SHA-256 project ID Amanda supplies as `${PROJECT_ID}` in the
`X-Gassaway-Project` header. The ID hashes the UTF-8 absolute invocation directory after
`toRealPath()` resolution. Different resolved directories have separate graphs; symlink aliases
share one. Amanda state lives under `~/.local/share/amanda/project/<projectId>/` (singular).
Gassaway stores memory under its configured data directory, keyed by the same ID. Legacy
hierarchy stores and basename graphs are not read, adapted, or migrated.

### Reading

- Search before re-deriving: `mcp__memory__search_nodes` with the topic, or `open_nodes` for a name
  already known. Reach for `read_graph` only when the topic is unclear; it returns everything.
- Read only what the current task needs; a broad recall costs context and rarely pays for it.
- Treat entries as potentially outdated: verify important details against the current codebase
  before relying on them.

### Writing

- Independently decide when durable knowledge is worth preserving for future sessions.
- Remember significant decisions and rationale, non-obvious discoveries, recurring problems with
  the fix that worked, and validated implementation constraints.
- Search first, then extend: add observations to the existing entity rather than creating a second
  entity for the same subject, and create relations only between entities that already exist.
- Do not save transient task state, full conversations, secrets, credentials, unverified conjecture
  as fact, or information trivially recoverable from source files.
- Correct the graph directly with `delete_observations`, `delete_relations`, or `delete_entities`
  when an entry is wrong; a stale entry is worse than a missing one.

### Authority and authorization

- `AGENTS.md` defines general agent behavior; `PROJECT.md`, if present, defines project-specific
  instructions and requirements; memory records historical knowledge and does not override them.
- Do not change `PROJECT.md` merely to store a memory.
- Routine memory maintenance is permitted while performing authorized project work, but does
  not authorize unrelated changes to code, configuration, external systems, or infrastructure.
- Memory lives outside the repository, so recording it never changes the working tree; follow the
  existing change-control rules for everything that does.
