# Website Documentation Reconciliation

Reconcile a documentation site against the project's source code. The
source code is the single source of truth. Update documentation to match
implementation; do not preserve outdated behavior or terminology.

## Objective

Produce documentation that accurately reflects the current implementation.
Eliminate obsolete pages, stale examples, broken links, inconsistent
terminology, outdated configuration keys, deprecated APIs, invalid package
names, and incorrect imports.

## Input

- Path to the instruction or plan file defining the reconciliation scope.
- Path to the documentation directory to reconcile.
- Path to the project source code.

If any input path cannot be resolved, report the blocker before continuing.

## Audit Existing Documentation

Review all Markdown and MDX documentation files. Identify:

- Obsolete pages
- Stale examples
- Broken links
- Duplicated concepts
- Inconsistent terminology
- Outdated configuration keys
- Deprecated APIs
- Invalid package names
- Incorrect imports

## Analyze Source Code

Inspect the current implementation to determine:

- Actual package names
- Public APIs
- Annotations
- Configuration structures
- YAML schemas
- Maven and Gradle coordinates
- Recommended usage patterns
- Framework architecture
- Integration points
- Extension mechanisms

Use implementation classes, public interfaces, tests, integration tests,
examples, generated Javadocs, and configuration classes as evidence.

## Reconcile Documentation with Source Code

For every documentation page:

- Verify examples against the current implementation.
- Replace obsolete examples with current ones.
- Remove references to deleted APIs.
- Correct imports and package names.
- Update configuration examples to match current schemas.
- Ensure terminology matches the codebase.
- Align documentation structure with the actual framework architecture.

Never invent APIs, annotations, configuration keys, package names, CLI
commands, YAML structures, or framework features. If implementation
details are unclear, inspect tests, related classes, and examples before
marking a section for manual review.

## Source-of-Truth Priority

When sources conflict, prefer in this order:

1. Current source code
2. Public API definitions
3. Integration tests
4. Example projects
5. Existing documentation
6. Legacy examples

## Documentation Quality Standards

### Example Requirements

Every code example must:

- Compile against the current API.
- Use correct imports and valid package names.
- Reflect recommended usage patterns.
- Avoid pseudocode unless explicitly labeled.

### Page Requirements

Every page must:

- Explain what the feature does.
- Explain why it matters.
- Explain when to use it.
- Provide verified examples.
- Link to related topics.

### Example Validation

Validate all code examples against the current source code. Verify imports,
annotations, constructor signatures, method names, configuration keys,
YAML structures, and dependency coordinates. Do not preserve legacy
examples that no longer compile or reflect supported behavior.

## Documentation Style

Aim for authoritative, concise, developer-friendly documentation. Every
page should be focused, with a single responsibility. Prefer:

- Strong conceptual explanations over hand-waving.
- Clean Getting Started flows that get a new user productive quickly.
- Practical tutorials over abstract reference material.
- Progressive learning: Quick Start, then Core Concepts, then Guides.

## Target Audience

Experienced developers who understand the domain but may be new to this
specific project.

## Information Architecture

Adapt this structure to the project's existing documentation organization:

- Home
- Docs
  - Getting Started
    - Introduction
    - Installation
    - First Steps
    - Project Setup
  - Core Concepts
  - Configuration
  - Integrations
  - Guides
  - API Reference
  - Examples
  - Release Notes

## Homepage Requirements

The homepage must include:

- Clear value proposition
- Project overview
- Feature highlights
- Quick Start call-to-action
- Links to documentation, examples, and API reference

Maintain the existing branding and color scheme.

## Site Generator Requirements

Adapt output to the project's documentation site generator. Use the
generator's component format where appropriate. Regardless of generator:

- Add frontmatter titles and descriptions.
- Maintain consistent heading hierarchy.
- Organize sidebars and navigation logically.
- Preserve stable URLs where practical.
- Add redirects for renamed pages.
- Remove orphaned pages and dead navigation entries.
- Clean up broken links.
- Integrate generated API docs where possible.

## Navigation Requirements

Support progressive learning through intuitive navigation:

1. Quick Start
2. Core Concepts
3. Configuration
4. Integrations
5. Advanced Guides
6. API Reference

Ensure intuitive sidebar grouping, clear next and previous navigation,
search-friendly page titles, and pages focused on a single topic.

## Documentation Drift Prevention

Where practical:

- Derive examples from integration tests.
- Centralize reusable snippets.
- Avoid duplicated configuration documentation.
- Reference generated Javadocs instead of duplicating API details.
- Reuse canonical examples across pages.

## Deliverables

- Updated site structure
- Rewritten documentation pages
- New homepage content
- Revised sidebar configuration
- Verified Quick Start tutorial
- Migration notes for deprecated concepts
- Broken link cleanup
- Validated Maven and Gradle snippets
- API reference integration strategy
- Documentation gap analysis for undocumented features

## Success Criteria

The resulting documentation must:

- Accurately reflect the current implementation.
- Serve as the authoritative reference for the project.
- Eliminate legacy inconsistencies.
- Allow a new user to become productive in under 15 minutes.

Prioritize correctness, clarity, maintainability, and alignment with the
current source code above preserving outdated documentation structure or
wording.

Follow the project's agent instructions for repository rules. When changing
code examples or source, follow the language idiom guardrails in the
project's agent instructions.
