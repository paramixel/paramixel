---
description: Sync the spec files to reflect recent code changes
agent: build
---
The codebase has changed. Scan the recent modifications and update the spec files
under spec/ to reflect the current state of the system.

Steps:
1. Identify which spec files are stale by comparing their content to the current code.
   - Check spec/00-overview.md: module table, technology stack, directory layout.
   - Check spec/01-architecture.md: component diagram, data flow, ADRs, cross-cutting concerns.
   - Check spec/02-data-model.md: all interfaces, annotations, concrete classes, and config properties.
   - Check spec/03-api-contracts.md: annotation signatures, method-level contracts, error catalog.
   - Check spec/04-modules.md: package structure, key classes, boundaries for each module.
   - Check spec/05-conventions.md: naming patterns, mandatory rules, prohibited patterns.
   - Check spec/06-testing.md: test templates, Testcontainers usage, new infrastructure.

2. For each stale file, rewrite only the sections that changed — preserve all other content.

3. Ensure AGENTS.md still accurately reflects the critical rules, module boundaries, and build commands.
   Update AGENTS.md if needed.

4. Report a summary of every section that was updated and why.
