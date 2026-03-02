---
description: Verify code conforms to the system specification
agent: build
subtask: true
---
Audit the current codebase against the system specification in spec/.

Checks to perform:

1. **Conventions** (spec/05-conventions.md):
   - All Java files in api/, engine/, maven-plugin/ start with the Apache 2.0 license header.
   - All public API types live in org.paramixel.api package (api module only).
   - Engine classes live in the correct org.paramixel.engine.<layer> sub-package.
   - No field injection annotations (@Autowired, @Inject) in any production class.
   - No System.out.println in engine or api production code outside ParamixelEngineExecutionListener.
   - No @SuppressWarnings annotations in production code.
   - Class names follow the documented naming patterns (Concrete* prefix for implementations, Paramixel* prefix for engine types, etc.).
   - All constructor parameters that must be non-null are annotated @NonNull and guarded with Objects.requireNonNull().

2. **Module boundaries** (spec/04-modules.md):
   - paramixel-api does not import any org.paramixel.engine.* class.
   - paramixel-engine does not import org.paramixel.maven.plugin.* class.
   - paramixel-maven-plugin does not import org.paramixel.engine.* internal implementation classes (only uses the engine as a runtime dependency via the JUnit Platform).
   - paramixel-tests does not use JUnit Jupiter @Test annotations.
   - paramixel-examples does not contain engine unit tests.

3. **API contracts** (spec/03-api-contracts.md):
   - Every annotation documented in the spec exists in org.paramixel.api.Paramixel.
   - MethodValidator validates all documented lifecycle annotations.
   - ParamixelTestEngine.getId() returns exactly "paramixel".
   - ParamixelMojo goal name is "test".

4. **Data model** (spec/02-data-model.md):
   - All documented interfaces (ArgumentContext, ClassContext, EngineContext, Store, Named, ArgumentsCollector) exist in org.paramixel.api.
   - NamedValue<T> implements Named and has of(String, T) factory method.
   - ConcreteStore is backed by ConcurrentHashMap and null-puts remove the key.
   - ConcreteClassContext has AtomicInteger for invocationCount, successCount, failureCount and AtomicReference<Throwable> for firstFailure.

5. **Test coverage** (spec/06-testing.md):
   - Every production class in org.paramixel.engine.* has a corresponding *Test class in engine/src/test/java/.
   - No @Paramixel.TestClass annotated classes appear in engine/src/main/java/ or maven-plugin/src/main/java/.
   - paramixel-tests classes do not import org.junit.jupiter.api.Test.

Report violations as a numbered list with file path and line number where applicable.
If no violations are found, state: "Specification conformance check passed."
