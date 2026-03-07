# Paramixel -- Coding Conventions

## 1. Package Naming

| Module | Root Package | Sub-packages |
|---|---|---|
| `paramixel-api` | `org.paramixel.api` | None (flat) |
| `paramixel-engine` | `org.paramixel.engine` | `api`, `configuration`, `descriptor`, `discovery`, `execution`, `filter`, `invoker`, `listener`, `util`, `validation` |
| `paramixel-maven-plugin` | `org.paramixel.maven.plugin` | None |
| `paramixel-tests` | `test` (or `test.<subpackage>`) | `argument`, `lifecycle`, `named`, `order`, `store`, `tags` |
| `paramixel-examples` | `examples` | `simple`, `complex`, `support`, `testcontainers.*` |
| `paramixel-benchmarks` | `org.paramixel.engine` | `api`, `filter`, `util` |

New engine classes MUST go into the sub-package matching their layer (e.g., a new runner
class -> `org.paramixel.engine.execution`). New API types MUST go into `org.paramixel.api`.

The `paramixel-benchmarks` module intentionally uses the `org.paramixel.engine.*` namespace
and places sources under `benchmarks/src/main/java` for JMH discovery.

## 2. Class Naming Conventions

| Category | Pattern | Examples |
|---|---|---|
| Public API interfaces | Plain noun | `ArgumentContext`, `ClassContext`, `Store`, `Named` |
| Public API value object | Plain noun | `NamedValue` |
| Concrete context impl | `Concrete` prefix | `ConcreteEngineContext`, `ConcreteStore` |
| JUnit descriptor types | `Paramixel` prefix + subject + `Descriptor` | `ParamixelTestClassDescriptor` |
| Abstract base descriptor | `Abstract` prefix | `AbstractParamixelDescriptor` |
| Discovery | `Paramixel` prefix + `Discovery` | `ParamixelDiscovery` |
| Runners | `Paramixel` prefix + subject + `Runner` | `ParamixelClassRunner` |
| Execution support | `Paramixel` prefix + noun | `ParamixelExecutionRuntime` |
| Invokers | `Paramixel` prefix + `Invoker` | `ParamixelReflectionInvoker` |
| Listeners | `Paramixel` prefix + subject + `Listener` | `ParamixelEngineExecutionListener` |
| Abstract listener base | `Abstract` prefix + noun | `AbstractEngineExecutionListener` |
| Validators | Subject + `Validator` | `MethodValidator` |
| Utilities | Subject + purpose | `FastIdUtil` |
| Maven Mojo | Subject + `Mojo` | `ParamixelMojo` |
| Test engine entry point | `Paramixel` + `TestEngine` | `ParamixelTestEngine` |
| Functional test classes | `<Feature>Test` or `<Feature>Test<N>` | `BasicTest`, `StoreTest1` |
| Example test classes | `<Scenario>Test` | `KafkaTest`, `ParallelArgumentTest` |
| Test environment helpers | Subject + `TestEnvironment` | `KafkaTestEnvironment` |
| Inner cache-key classes | `<Usage>CacheKey` | `LifecycleCacheKey` |
| Inner permit classes | Subject + `Permit` | `ClassPermit`, `ArgumentPermit` |

New suffix patterns MUST NOT be introduced without updating this spec.

## 3. Dependency Injection

All dependencies MUST be injected through constructors. All injected fields MUST be `final`.

```java
// CORRECT
public final class ParamixelClassRunner {
    private final ParamixelExecutionRuntime runtime;
    private final ConcreteEngineContext engineContext;

    public ParamixelClassRunner(
            final ParamixelExecutionRuntime runtime,
            final ConcreteEngineContext engineContext) {
        this.runtime = runtime;
        this.engineContext = engineContext;
    }
}
```

There is no IoC container. Object graphs are wired manually in `ParamixelTestEngine.execute()`.

## 4. Null Safety

- All public method parameters that MUST NOT be null MUST be annotated `@NonNull` from
  `org.jspecify.annotations`. `@NonNull` MUST NOT be used on primitive parameters.
- Constructors MUST use `Objects.requireNonNull(param, "param must not be null")` for each
  non-null reference-type parameter.
- Return values that may be null MUST be documented in Javadoc.
- Nullable parameters are documented but not annotated `@Nullable`.

## 5. Exception Handling

There is no global exception handler. Each execution layer handles exceptions explicitly.
See `08-error-handling.md` for the complete error contract.

- All exceptions MUST be unchecked at module boundaries.
- `throws Throwable` is acceptable in internal invoker signatures.
- `@SuppressWarnings` MUST NOT be used in production code. It MAY be used in test sources
  (`*/src/test/java/**`) when necessary.

## 6. Javadoc Standards

Javadoc requirements apply to production sources (`*/src/main/java/**`).
In test sources (`*/src/test/java/**`), Javadoc is optional.

### Coverage

- All types (`class`, `interface`, `enum`, `record`) MUST have Javadoc, regardless of scope.
- All member variables (fields) MUST have Javadoc, regardless of scope.
- All methods and constructors (public and private) MUST have Javadoc.
- **Exception:** Methods annotated with `@Override` MUST NOT have Javadoc.

### Completeness

Every Javadoc block MUST document:
- `@param` for every parameter.
- `@return` for every non-void method.
- `@throws` for every exception the method/constructor explicitly throws.
- Field Javadoc MUST NOT use `@param` or `@return`.
- Constructor Javadoc MUST NOT use `@return`.

### Tags

- `@author` MUST appear only on type-level Javadoc for `class`, `interface`, or `enum`.
  It MUST NOT appear on `record`, annotation, field, method, or constructor Javadoc.
- `@author` MUST include name and email: `@author Full Name (email@domain)`.
- `@since` is the only API stability tag supported by Paramixel.
- In `paramixel-api` production sources (`api/src/main/java/**`):
  - `@since` MUST appear on every public type, method, constructor, and field Javadoc.
  - `@since` MUST NOT appear on any non-public (private, protected, or package-private) declaration.
- In all other modules (engine, maven-plugin, tests, examples, benchmarks):
  - `@since` MUST NOT appear in any Javadoc block.
- `@apiNote`, `@implSpec`, `@implNote` MAY be used when appropriate.

### Format

- Javadoc MUST use block (multi-line) format. Single-line `/** ... */` MUST NOT be used.
- Javadoc blocks MUST NOT contain an empty line immediately before the closing `*/`.
- All `{@link}` references MUST resolve. Use fully-qualified names when necessary.
- Javadoc MUST appear only immediately preceding a declaration.
- There MUST be a blank `*` line between prose content and the first `@` tag.
- Use consistent, professional tone with complete sentences.

## 7. Logging

| Module | Logger Type | Usage |
|---|---|---|
| `paramixel-engine` | `java.util.logging.Logger` via `Logger.getLogger(ClassName.class.getName())` | `FINE` for normal flow, `WARNING` for lifecycle exceptions, `SEVERE` for fatal failures |
| `paramixel-maven-plugin` | `AbstractMojo.getLog()` | `info`, `warn` for Mojo-level events |
| Examples / tests | Custom `examples.support.Logger` | printf-style wrapper |

`System.out.println` MUST NOT be used in engine/api/plugin production code except in
`ParamixelEngineExecutionListener` for deliberate console output in Maven mode.

SLF4J API is a declared dependency but JUL is the primary logger. Do not use SLF4J
`Logger` directly in engine code.

## 8. Code Style and Formatting

- **Formatter:** Palantir Java Format 2.87.0 (PALANTIR style).
- **Applied at:** Every `compile` phase via `spotless-maven-plugin:apply`.
- **License header:** Apache 2.0, from `assets/license-header.txt`, on all Java files.
- **Encoding:** UTF-8.
- Code MUST NOT be manually formatted. Run `./mvnw compile` or `./mvnw spotless:apply`.
- Avoid redundant `toString()` calls in string concatenation.

## 9. `final` Usage

- All fields set in constructors and never mutated MUST be `final`.
- All utility classes (static methods only) MUST be `final` with a private no-arg constructor.
- All concrete implementation classes not designed for extension MUST be `final`.
- Method parameters MUST be `final` throughout engine and plugin source.

## 10. Explicit Constructors

All classes in production sources (`*/src/main/java/**`) MUST explicitly declare at least
one constructor. Do not rely on the implicit default constructor.

## 11. Empty Bodies

Any empty method or constructor body MUST contain the comment `// INTENTIONALLY EMPTY`.

---

## Mandatory Rules Summary

1. Every public method reference-type parameter MUST have `@NonNull` and constructors MUST
   validate with `Objects.requireNonNull()`.
2. All Java source files MUST start with the Apache 2.0 license header.
3. All classes MUST be formatted with Palantir Java Format (commit only after `./mvnw compile`).
4. Every new production class in `paramixel-engine` MUST have a corresponding unit test.
5. Constructor injection MUST be used for all dependencies.
6. `final` MUST be used on constructor-set fields and utility/implementation classes.
7. `@SuppressWarnings` MUST NOT be used in production code.
8. New public API types MUST live in `org.paramixel.api` (in `paramixel-api` module only).
9. Engine-internal types MUST live in `org.paramixel.engine.<layer>` sub-packages.
10. Lifecycle exceptions MUST be recorded via `classContext.recordFailure(t)`.
11. Error messages mentioning Paramixel annotations MUST use `@Paramixel.<Name>` form.
12. Empty method/constructor bodies MUST contain `// INTENTIONALLY EMPTY`.
13. All production classes MUST declare explicit constructors.

---

## Prohibited Patterns

1. MUST NOT use `@Autowired`, `@Inject`, `@Component`, `@Service`, or any Spring/CDI annotation.
2. MUST NOT use field injection.
3. MUST NOT add `System.out.println` in engine/api production code outside
   `ParamixelEngineExecutionListener`.
4. MUST NOT catch `Throwable` and swallow it without recording via `classContext.recordFailure(t)`
   or logging at `WARNING` or higher.
5. MUST NOT reference engine-internal packages (`org.paramixel.engine.*`) from `paramixel-api`.
6. MUST NOT reference `paramixel-maven-plugin` or `paramixel-engine` from `paramixel-api`.
7. MUST NOT add `@Paramixel.TestClass`-annotated classes to `paramixel-engine` or
   `paramixel-maven-plugin` source.
8. MUST NOT use JUnit Jupiter annotations (`@org.junit.jupiter.api.Test`, etc.) in any module
   except engine unit tests (which use standard `org.junit.jupiter.api.*`).
9. MUST NOT call `method.setAccessible(true)` outside `ParamixelReflectionInvoker` in
   production code, with two exceptions:
   - `constructor.setAccessible(true)` is permitted in `ParamixelClassRunner` for test class
     instantiation.
   - Test sources (`*/src/test/java/**`) MAY use `setAccessible(true)`.
10. MUST NOT bypass `ParamixelConcurrencyLimiter` when submitting class or argument tasks.
11. MUST NOT modify a `paramixel-api` interface without updating `03-domain-model.md` and
    `04-lifecycle.md`.
12. MUST NOT add a new Maven dependency without explicit user approval and spec update.

---

## Testing Requirements Per Change Type

| Change Type | Required Tests |
|---|---|
| New API interface | Unit test for concrete implementation in `engine` module |
| New API annotation | Unit test in `engine` + functional test in `paramixel-tests` |
| New engine class | Unit test in `engine/src/test/java/` mirroring production package |
| New engine execution feature | Unit test + functional test in `paramixel-tests` |
| New lifecycle hook | Functional test in `paramixel-tests/test/lifecycle/` |
| Bug fix | Unit test demonstrating the bug before fix; must pass after fix |
| New example | New class in `paramixel-examples` (no engine unit test required) |
| Maven plugin change | Manual test via `./mvnw test -pl tests` or `./mvnw test -pl examples` |
