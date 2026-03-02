# Reverse Engineering Prompt — Java 21 Multi-Module Maven Project

Copy the entire content of this file as a prompt into OpenCode (or any capable LLM agent)
while working inside your project root. It will analyze the codebase and produce a full
specification suite under `spec/` and support files wired for OpenCode.

---

## PROMPT (start copying below this line)

You are a senior software architect. Your task is to reverse engineer this Java 21
multi-module Maven project and produce a complete, authoritative system specification.
The specification will be the single source of truth used by OpenCode (an AI coding agent)
to implement new features correctly, consistently, and without requiring re-exploration of
the codebase each session.

Work methodically through every step below. Do not skip any section. After completing each
file, confirm it was written before moving on.

---

### STEP 1 — Discover the project layout

Read the following and collect findings before writing anything:

1. The root `pom.xml` — identify the project `groupId`, `artifactId`, `version`, packaging
   (`pom`), and every `<module>` entry.
2. Each module's `pom.xml` — collect: `artifactId`, `packaging` (jar/war/etc.),
   declared `<dependencies>` (groupId:artifactId only, no versions needed),
   and any Maven plugins configured in `<build><plugins>`.
3. The top-level source tree of each module (`src/main/java/` package structure to depth 3).
4. Any configuration directories: `src/main/resources/`, `src/test/resources/`.
5. Any infrastructure or deployment files at the repo root (Dockerfile, docker-compose.yml,
   Kubernetes manifests, CI/CD pipelines, Makefile, shell scripts, etc.).

Record a module dependency graph: which module depends on which other module(s).

---

### STEP 2 — Understand the architecture

For each module, read enough source code to answer:

1. What is this module's single responsibility?
2. What architectural pattern is used (layered/hexagonal/event-driven/CQRS/etc.)?
3. What are the primary entry points (REST controllers, message listeners, scheduled jobs,
   CLI main classes, gRPC services, GraphQL resolvers, etc.)?
4. What persistence technology is used (JPA/Hibernate, JDBC, jOOQ, MongoDB, Redis, etc.)?
   Identify the datasource(s).
5. What messaging or eventing is used (Kafka, RabbitMQ, JMS, Spring Events, etc.)?
6. What security mechanism is used (Spring Security, JJWT, OAuth2, API keys, mTLS, etc.)?
7. What cross-cutting concerns exist (logging framework, metrics/tracing, caching,
   transaction management, validation framework)?
8. What Java 21 features are actively used (records, sealed classes, pattern matching,
   virtual threads, text blocks, structured concurrency)?

---

### STEP 3 — Map the domain model

Read entity classes, records, value objects, and DTOs. For each:

1. Name and package.
2. Fields (name, type, nullable or required).
3. Relationships to other entities (one-to-one, one-to-many, many-to-many).
4. Persistence annotations or mapping strategy.
5. Any validation constraints (`@NotNull`, `@Size`, custom validators).

Produce a textual entity-relationship summary (not a diagram — plain text table or list
that an LLM can parse unambiguously).

---

### STEP 4 — Document public API contracts

For every external-facing API surface:

**REST / HTTP**
- HTTP method, path, path variables, query params.
- Request body schema (field names, types, required/optional).
- Response body schema and HTTP status codes.
- Authentication / authorization requirements.
- Known error response shapes.

**Messaging (Kafka / RabbitMQ / etc.)**
- Topic / queue / exchange name.
- Message schema (key type, value type/class, encoding: JSON/Avro/Protobuf).
- Producer module → consumer module direction.

**Shared library APIs**
- Public interfaces and abstract classes in shared/common/core modules.
- Their method signatures and contract (what they guarantee, what they throw).

---

### STEP 5 — Identify coding conventions

Read at least 5 representative source files per module and capture:

1. Package naming pattern (e.g., `com.example.<module>.<layer>`).
2. Class naming conventions (suffix patterns: `Service`, `Repository`, `Controller`,
   `Handler`, `Mapper`, `Config`, `Exception`, etc.).
3. Dependency injection style (constructor injection only? field injection? Lombok?).
4. Exception handling strategy (global handler? checked vs. unchecked? custom hierarchy?).
5. Logging convention (which logger, log levels used, MDC fields).
6. DTO/mapping strategy (MapStruct? manual? records?).
7. Test structure: unit vs. integration test split, naming conventions, test framework
   (JUnit 5, Mockito, Testcontainers, AssertJ, REST-assured, etc.).
8. Build conventions: are there shared parent POM configurations, enforcer rules,
   checkstyle/PMD/SpotBugs configs?

---

### STEP 6 — Capture non-functional requirements visible in the code

Look for evidence of:

1. Target SLAs or performance budgets (timeouts configured, circuit breakers, retries).
2. Scalability assumptions (stateless services? sticky sessions? distributed caching?).
3. Observability setup (Micrometer metrics, OpenTelemetry, log aggregation config).
4. Security controls (CORS config, CSRF, rate limiting, input sanitization patterns).
5. Data retention or compliance markers (soft-delete patterns, audit fields, PII handling).

---

### STEP 7 — Write the specification files

Now write the following files. Each file must be self-contained and written in clean
Markdown so OpenCode can load it as a context instruction.

#### `spec/00-overview.md`
- Project name, purpose in 2–3 sentences.
- Module inventory table: module name | packaging | responsibility | depends on.
- Technology stack summary (language version, framework, build tool, key libraries).
- Repository layout map (top-level directories and their purpose).

#### `spec/01-architecture.md`
- Architectural style (with justification if visible in code or comments).
- Component interaction diagram in ASCII or Mermaid code block.
- Data flow narrative: how a typical request moves through the system end-to-end.
- Key architectural decisions visible in the codebase (ADRs if implied).
- Cross-cutting concerns and how they are implemented.

#### `spec/02-data-model.md`
- All domain entities with their fields and types.
- Entity relationships (plain text ER summary).
- Persistence mapping notes (table names, discriminator columns, inheritance strategies).
- Enumerations and their values.
- Key value objects / DTOs that cross module boundaries.

#### `spec/03-api-contracts.md`
- Complete REST API inventory (one subsection per controller/resource).
- Messaging contracts (topics, schemas, producer/consumer).
- Shared library interfaces with method-level contracts.
- Error response catalog.

#### `spec/04-modules.md`
- One section per Maven module.
- Responsibility, internal package structure, layer breakdown.
- Key classes and their roles.
- What this module MUST NOT do (boundaries / anti-patterns).
- Configuration properties owned by this module.

#### `spec/05-conventions.md`
- All conventions discovered in Step 5.
- Mandatory rules (things that MUST always be done).
- Prohibited patterns (things that MUST NEVER be done).
- How to add: a new entity, a new REST endpoint, a new Kafka consumer, a new module.
  Write each as a numbered checklist.
- Testing requirements per change type.

#### `spec/06-testing.md`
- Test pyramid for this project (unit / integration / e2e proportions and tools).
- How to run tests locally (exact Maven commands using `./mvnw`).
- Testcontainers usage and which infrastructure is spun up.
- Coverage thresholds if configured.
- Patterns for mocking external services.
- Contract testing if present.

---

### STEP 8 — Write OpenCode support files

#### `AGENTS.md` (project root)

**Overwrite** any existing `AGENTS.md` completely. Write the full file from scratch
so OpenCode always has the right mental model. Use this exact structure, filled in
with values discovered from the codebase:

```
# <Project Name> — Agent Rules

## Specification
This project uses specification-oriented development. The authoritative system
specification lives in `spec/`. Before implementing any feature, you MUST read the
relevant spec files. The spec files are loaded automatically via `opencode.json`.

## Critical Rules
- Always follow the conventions in `spec/05-conventions.md`.
- Never introduce a dependency not already present in a module's `pom.xml` without
  explicit user approval.
- New classes must follow the naming and packaging conventions in `spec/05-conventions.md`.
- All new code must have tests as described in `spec/06-testing.md`.
- If a feature requires a data model change, update `spec/02-data-model.md` after
  implementing it.
- If a feature adds or changes an API, update `spec/03-api-contracts.md` after
  implementing it.
- Build the project with `./mvnw verify` after every significant change and fix all errors
  before declaring the task complete.

## Module Boundaries
<paste the module boundary rules from spec/04-modules.md here — one line per module>

## How to Navigate This Codebase
- Source root per module: `<module>/src/main/java/`
- Tests per module: `<module>/src/test/java/`
- Shared types live in: `<shared-module>/`
- Application entry point(s): <list main class(es)>

## Build Commands

| Intent | Command |
|---|---|
| Full build + all tests | `./mvnw verify` |
| Compile only | `./mvnw compile` |
| Single module tests | `./mvnw test -pl <module>` |
| Skip tests | `./mvnw package -DskipTests` |
| Run a single test class | `./mvnw test -pl <module> -Dtest=MyTest` |
| Generate sources (e.g. MapStruct) | `./mvnw generate-sources` |

## What OpenCode Should NOT Do

- Do not refactor code outside the scope of the requested task.
- Do not rename existing public APIs without explicit instruction.
- Do not change existing database schema migrations without explicit instruction.
- Do not add `@SuppressWarnings` to hide compiler or static analysis warnings.
- Do not use field injection (`@Autowired` on fields) — constructor injection only.
- Do not commit or push changes; only make edits.
```

#### `opencode.json` (project root)

Wire all spec files as persistent instructions:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "instructions": [
    "spec/00-overview.md",
    "spec/01-architecture.md",
    "spec/02-data-model.md",
    "spec/03-api-contracts.md",
    "spec/04-modules.md",
    "spec/05-conventions.md",
    "spec/06-testing.md"
  ]
}
```

#### `.opencode/commands/implement-feature.md`

```markdown
---
description: Implement a feature from the system specification
agent: build
---
Implement the following feature in this Java 21 Maven project: $ARGUMENTS

Before writing any code:
1. Read spec/00-overview.md to orient yourself.
2. Read spec/04-modules.md to identify which module(s) are affected.
3. Read spec/05-conventions.md to understand naming, packaging, and structural rules.
4. Read spec/03-api-contracts.md if the feature touches any API surface.
5. Read spec/02-data-model.md if the feature touches any entity or database.

Then implement the feature:
- Follow ALL conventions in spec/05-conventions.md exactly.
- Do not add Maven dependencies without stating them explicitly for user approval.
- Write unit and integration tests per spec/06-testing.md.
- Run `./mvnw verify -pl <affected-module(s)>` and fix all failures before finishing.
- After implementation, update spec/02-data-model.md and spec/03-api-contracts.md
  if anything changed in those areas.
```

#### `.opencode/commands/update-spec.md`

```markdown
---
description: Sync the spec files to reflect recent code changes
agent: build
---
The codebase has changed. Scan the recent modifications and update the spec files
under spec/ to reflect the current state of the system.

Steps:
1. Identify which spec files are stale by comparing their content to the current code.
2. For each stale file, rewrite only the sections that changed — preserve all other content.
3. Files to consider: spec/00-overview.md, spec/01-architecture.md, spec/02-data-model.md,
   spec/03-api-contracts.md, spec/04-modules.md, spec/05-conventions.md, spec/06-testing.md.
4. Report a summary of every section that was updated and why.
```

#### `.opencode/commands/spec-check.md`

```markdown
---
description: Verify code conforms to the system specification
agent: build
subtask: true
---
Audit the current codebase against the system specification in spec/.

Checks to perform:
1. Conventions (spec/05-conventions.md): package names, class suffixes, DI style,
   exception handling, logging.
2. Module boundaries (spec/04-modules.md): no module imports classes it should not.
3. API contracts (spec/03-api-contracts.md): every documented endpoint exists and
   matches the documented signature.
4. Data model (spec/02-data-model.md): every documented entity exists with the
   documented fields.
5. Test coverage (spec/06-testing.md): verify test files exist for new production classes.

Report violations as a numbered list with file path and line number.
If no violations are found, state: "Specification conformance check passed."
```

---

### STEP 9 — Final validation

After writing all files:

1. Re-read `AGENTS.md` — does it give a new session of OpenCode enough context to
   start implementing a feature without asking clarifying questions about the project?
2. Re-read `spec/05-conventions.md` — does it have enough detail that a developer
   who has never seen this codebase could add a new endpoint correctly?
3. If either answer is "no", strengthen the relevant sections.
4. List every file written, its path, and its approximate line count as a final summary.

---

## END OF PROMPT
