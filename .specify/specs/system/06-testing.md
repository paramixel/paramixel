# Paramixel — Testing Guide

---

## 1. Test Pyramid

Paramixel uses two distinct test layers. There is no traditional "e2e" layer (no running server to test against).

```
┌─────────────────────────────────────────────┐
│         Functional / Integration Tests        │
│  paramixel-tests  &  paramixel-examples       │
│  ~ 60 test classes; executed by own plugin    │
│  Exercises full engine + plugin pipeline      │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│              Unit Tests                       │
│  paramixel-engine/src/test/java/              │
│  paramixel-api/src/test/java/                 │
│  ~ 20 unit test classes                       │
│  Exercises individual engine classes          │
└─────────────────────────────────────────────┘
```

| Layer | Location | Tool | Purpose |
|---|---|---|---|
| Unit | `engine/src/test/java/`, `api/src/test/java/` | JUnit Jupiter + AssertJ via Maven Surefire | Test individual classes in isolation |
| Functional | `tests/src/test/java/`, `examples/src/test/java/` | `@Paramixel.TestClass` classes run by `paramixel-maven-plugin` | Validate engine lifecycle, argument handling, concurrency, store scoping |

---

## 2. Running Tests Locally

### Full build and all tests

```bash
./mvnw verify
```

Runs unit tests (Surefire) in `api` and `engine`, then runs functional tests (Paramixel plugin) in `tests` and `examples`.

### Compile only (no tests)

```bash
./mvnw compile
```

Note: Spotless `apply` also runs during compile, so this also formats all code.

### Unit tests for a single module

```bash
# engine unit tests only
./mvnw test -pl engine

# api unit tests only
./mvnw test -pl api
```

### Functional tests for tests module

```bash
./mvnw test -pl tests
```

This runs the `paramixel-maven-plugin:test` goal. Surefire is disabled for this module.

### Functional tests for examples module

```bash
./mvnw test -pl examples
```

Note: examples include Testcontainers-based tests that require Docker. Ensure Docker daemon is running.

### Skip all tests

```bash
./mvnw package -DskipTests
```

### Run a single unit test class (engine)

```bash
./mvnw test -pl engine -Dtest=ParamixelDiscoveryTest
```

### Run a single unit test method

```bash
./mvnw test -pl engine -Dtest=ParamixelDiscoveryTest#testDiscoverByClassSelector
```

### Generate sources (none required currently — no annotation processors)

```bash
./mvnw generate-sources
```

### Code coverage report (engine only)

```bash
./mvnw verify -pl engine
# Report written to: engine/target/site/jacoco/index.html
```

### Benchmarks (JMH)

Benchmarks live in the `benchmarks` module under `benchmarks/src/main/java` and are executed only when the benchmarks
profile is enabled.

```bash
./mvnw test -pl benchmarks -Pbenchmarks
```

---

## 3. Unit Test Structure (`engine` module)

Unit tests live in `engine/src/test/java/` mirroring the production package structure.

### Naming Convention

- Test class: `<ProductionClassName>Test.java` (e.g., `ConcreteStoreTest`, `ParamixelDiscoveryTest`).
- Test method names: descriptive camelCase or underscore-separated scenario names (e.g., `testPutAndGet`, `should_return_null_when_key_absent`).
- JUnit unit test classes MUST be declared `public`, and all JUnit-annotated methods (e.g., `@Test`, `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@AfterAll`) MUST be declared `public`.

### Framework

- **JUnit Jupiter** (`org.junit.jupiter:junit-jupiter`)
- **AssertJ** (`org.assertj:assertj-core`) for fluent assertions
- No Mockito (not in any `pom.xml`); tests use real collaborators or minimal hand-rolled stubs.

### Reflection in Tests

JUnit unit tests MAY use reflection helpers such as `setAccessible(true)` and `@SuppressWarnings` when needed to
validate internal behavior.

### Test Class Template (engine unit test)

```java
/*
 * [Apache 2.0 License Header]
 */

package org.paramixel.engine.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ConcreteStoreTest {

    @Test
    public void put_and_get_round_trip() {
        ConcreteStore store = new ConcreteStore();
        store.put("key", "value");
        assertThat(store.get("key")).isEqualTo("value");
    }

    @Test
    public void put_null_removes_key() {
        ConcreteStore store = new ConcreteStore();
        store.put("key", "value");
        store.put("key", null);
        assertThat(store.contains("key")).isFalse();
    }
}
```

### Surefire Configuration (engine)

```xml
<includes>
    <include>**/*Test.java</include>
</includes>
```

Surefire excludes the Paramixel engine from running during unit tests (the engine would discover and attempt to run the unit test classes as `@Paramixel.TestClass` otherwise):

```xml
<systemPropertyVariables>
    <junit.platform.excluded.engines>paramixel</junit.platform.excluded.engines>
</systemPropertyVariables>
```

JaCoCo agent is attached during unit tests for coverage collection.

---

## 4. Functional Test Structure (`tests` module)

Functional tests are authored as `@Paramixel.TestClass` classes and executed entirely by `paramixel-maven-plugin:test`. Surefire is disabled.

### What Makes a Good Functional Test

1. **Lifecycle counter assertions** — use `static AtomicInteger` counters to count how many times each lifecycle hook is called, then assert in `@Paramixel.Finalize`.
2. **Argument tracking** — use `ConcurrentSkipListSet<Integer>` to record observed argument indices; assert the full set in `@Paramixel.Finalize`.
3. **Store scoping** — write a value in `@Paramixel.BeforeAll` to the class store and assert it is visible in `@Paramixel.Test` and gone after `@Paramixel.Finalize` removes it.
4. **Concurrency verification** — use `ConcurrentHashMap` with argument-indexed entries; assert in `@Paramixel.Finalize` that all arguments were processed.

### Functional Test Template

```java
/*
 * [Apache 2.0 License Header]
 */

package test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class MyFeatureTest {

    private static final AtomicInteger testCount = new AtomicInteger(0);

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument("value-1");
        collector.addArgument("value-2");
    }

    @Paramixel.Test
    public void verify(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument(String.class)).startsWith("value-");
        testCount.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(testCount.get()).isEqualTo(2);
    }
}
```

### Test Sub-Packages

| Package | Purpose |
|---|---|
| `test` | Top-level lifecycle and basic argument tests |
| `test.argument` | Per-argument-type tests (null, arrays, collections, enums, etc.) |
| `test.lifecycle` | Lifecycle ordering and inheritance tests |
| `test.named` | `Named`/`NamedValue` display name tests |
| `test.order` | `@Paramixel.Order` annotation tests |
| `test.store` | Store scoping and `AutoCloseable` integration |

---

## 5. Testcontainers Usage (`examples` module)

Testcontainers is used exclusively in `paramixel-examples` (test scope only). Docker is required.

### Pattern

1. Create a `TestEnvironment` class (e.g., `KafkaTestEnvironment`) that implements `Named` and `AutoCloseable`. It wraps a Testcontainers container.
2. Return one or more `TestEnvironment` instances from an `@Paramixel.ArgumentsCollector`.
3. In `@Paramixel.BeforeAll`: call `testEnvironment.initialize(network)` to start containers.
4. In `@Paramixel.AfterAll`: call `testEnvironment.destroy()` with a `CleanupExecutor` for safe ordered cleanup.
5. The engine auto-calls `testEnvironment.close()` after `@Paramixel.AfterAll` if it implements `AutoCloseable`.

### Available container libraries (test scope in `examples/pom.xml`)

| Library | Purpose |
|---|---|
| `testcontainers:testcontainers` 2.0.3 | Base Testcontainers |
| `testcontainers:kafka` 1.21.4 | Kafka container |
| `testcontainers:mongodb` 1.21.4 | MongoDB container |
| `testcontainers:nginx` 1.21.4 | nginx container |
| `kafka-clients` 4.2.0 | Kafka producer/consumer |
| `mongodb-driver-sync` 5.6.4 | MongoDB sync driver |

### `CleanupExecutor` Pattern

```java
new CleanupExecutor()
    .addTask(testEnvironment::destroy)
    .addTaskIfPresent(
        () -> context.getClassContext().getStore().remove("network", Network.class),
        Network::close)
    .throwIfFailed();
```

This ensures all cleanup steps execute even if an earlier step throws, and rethrows the first failure.

---

## 6. Code Coverage

JaCoCo is configured only in the `engine` module.

| Configuration | Value |
|---|---|
| Plugin version | `jacoco-maven-plugin` 0.8.14 |
| Included packages | `org.paramixel.*` |
| Bootstrap classes | Excluded (`inclBootstrapClasses=false`) |
| Report phase | `verify` |
| Report location | `engine/target/site/jacoco/index.html` |

**No minimum coverage threshold is configured.** Coverage is reported but not enforced via a build-breaking rule. This may change in future versions.

---

## 7. Mocking Strategy

No mocking framework is declared. The test approach uses:

- **Real collaborators:** Tests instantiate real `ConcreteStore`, `ConcreteClassContext`, etc.
- **Minimal stub implementations:** Where a `TestEngine` or `EngineExecutionListener` is needed, simple anonymous classes or minimal concrete implementations are used.
- **Functional test classes:** The integration layer is tested end-to-end by the `paramixel-tests` module.

**Guideline:** For JUnit unit tests, never mock what you can use for real. Prefer real collaborators over stubs; use minimal hand-rolled stubs only when a real collaborator is impractical to construct or would make the test significantly slower or more brittle.

If Mockito is needed in the future, it must be added explicitly to the relevant module's `pom.xml` and this spec must be updated.

---

## 8. CI/CD Pipeline

| File | Trigger | Command |
|---|---|---|
| `.github/workflows/build.yaml` | Push to any branch; PR to `main` | `./mvnw -B clean verify` on Ubuntu 24.04, Java 25 Corretto |
| `.github/workflows/manual-build.yaml` | Manual trigger | (same command) |

All tests (unit + functional + examples) run in CI. A failing test fails the CI build.

---

## 9. Checklist for New Test Addition

### Adding a unit test to `paramixel-engine`

- [ ] Place test class in `engine/src/test/java/org/paramixel/engine/<package>/`.
- [ ] Name it `<ProductionClass>Test.java`.
- [ ] Use `@Test` from JUnit Jupiter; use AssertJ for assertions.
- [ ] Do NOT annotate with `@Paramixel.TestClass`.
- [ ] Ensure the class is discoverable by Surefire (`**/*Test.java` pattern).
- [ ] Run `./mvnw test -pl engine` to verify.

### Adding a functional test to `paramixel-tests`

- [ ] Place test class in `tests/src/test/java/test/` (or appropriate sub-package).
- [ ] Annotate with `@Paramixel.TestClass`.
- [ ] Use only `@Paramixel.*` annotations (NOT JUnit Jupiter `@Test`).
- [ ] Import `org.paramixel.api.*` only (no `org.paramixel.engine.*`).
- [ ] Use AssertJ assertions (`assertThat`).
- [ ] Run `./mvnw test -pl tests` to verify.

### Adding a Testcontainers example

- [ ] Place in `examples/src/test/java/examples/testcontainers/<service>/`.
- [ ] Create `<Service>TestEnvironment.java` implementing `Named` + `AutoCloseable`.
- [ ] Create `<Service>Test.java` annotated `@Paramixel.TestClass`.
- [ ] Add required Docker image to `src/test/resources/examples/testcontainers/<service>/docker-images.txt`.
- [ ] Use `CleanupExecutor` in `@Paramixel.AfterAll` for safe cleanup.
- [ ] Run `./mvnw test -pl examples` to verify (requires Docker).
