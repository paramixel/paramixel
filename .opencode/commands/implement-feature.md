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
5. Read spec/02-data-model.md if the feature touches any entity or data object.

Then implement the feature:
- Follow ALL conventions in spec/05-conventions.md exactly.
  - Every Java file MUST start with the Apache 2.0 license header from assets/license-header.txt.
  - Use constructor injection only — no field injection.
  - Annotate non-null parameters with @NonNull and guard with Objects.requireNonNull().
  - Use java.util.logging.Logger (JUL) in engine code; AbstractMojo.getLog() in plugin code.
  - Place new classes in the correct org.paramixel.<module>.<layer> sub-package.
  - Declare all dependency-holding fields as final.
  - Do NOT use @SuppressWarnings.
- Do not add Maven dependencies without stating them explicitly for user approval.
- Write unit and integration tests per spec/06-testing.md:
  - Engine unit tests → engine/src/test/java/org/paramixel/engine/<package>/<Class>Test.java using JUnit Jupiter + AssertJ.
  - Functional tests → tests/src/test/java/test/<Class>Test.java using @Paramixel.TestClass.
- Run `./mvnw verify -pl <affected-module(s)>` and fix all failures before finishing.
  - If touching api or engine, run `./mvnw verify -pl api,engine`.
  - If touching tests or examples, run `./mvnw verify` (full build).
- After implementation, update spec/02-data-model.md and spec/03-api-contracts.md if anything changed in those areas.
- Report what was implemented, what tests were added, and any outstanding concerns.
