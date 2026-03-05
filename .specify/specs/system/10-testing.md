# Paramixel -- Testing Guide

## Test Pyramid

Paramixel uses two test layers. There is no traditional end-to-end layer (no running
server to test against).

```
+---------------------------------------------+
|     Functional / Integration Tests           |
|  paramixel-tests  &  paramixel-examples      |
|  ~60 test classes; executed by own plugin     |
|  Exercises full engine + plugin pipeline      |
+---------------------------------------------+
+---------------------------------------------+
|            Unit Tests                        |
|  paramixel-engine/src/test/java/             |
|  paramixel-api/src/test/java/                |
|  ~20 unit test classes                       |
|  Exercises individual engine classes          |
+---------------------------------------------+
```

| Layer | Location | Tool | Purpose |
|---|---|---|---|
| Unit | `engine/src/test/java/`, `api/src/test/java/` | JUnit Jupiter + AssertJ via Maven Surefire | Test individual classes in isolation |
| Functional | `tests/src/test/java/`, `examples/src/test/java/` | `@Paramixel.TestClass` classes run by `paramixel-maven-plugin` | Validate lifecycle, argument handling, concurrency, store scoping |

---

## Running Tests

| Intent | Command |
|---|---|
| Full build + all tests | `./mvnw verify` |
| Compile only (also formats) | `./mvnw compile` |
| Engine unit tests only | `./mvnw test -pl engine` |
| API unit tests only | `./mvnw test -pl api` |
| Functional tests (tests module) | `./mvnw test -pl tests` |
| Functional tests (examples) | `./mvnw test -pl examples` (requires Docker) |
| Skip all tests | `./mvnw package -DskipTests -Dparamixel.skipTests=true` |
| Single unit test class | `./mvnw test -pl engine -Dtest=ParamixelDiscoveryTest` |
| Single unit test method | `./mvnw test -pl engine -Dtest=ParamixelDiscoveryTest#testDiscoverByClassSelector` |
| Code coverage report | `./mvnw verify -pl engine` -> `engine/target/site/jacoco/index.html` |
| Benchmarks (JMH) | `./mvnw test -pl benchmarks -Pbenchmarks` |

---

## Unit Test Structure (`engine` module)

Unit tests live in `engine/src/test/java/` mirroring the production package structure.

### Naming

- Test class: `<ProductionClassName>Test.java`
- Test methods: descriptive camelCase or underscore-separated scenario names
- Unit test classes MUST be `public`; all JUnit-annotated methods MUST be `public`

### Framework

- **JUnit Jupiter** (`org.junit.jupiter:junit-jupiter`) -- test-scope only
- **AssertJ** (`org.assertj:assertj-core`) for fluent assertions
- No Mockito. Tests use real collaborators or minimal hand-rolled stubs.

### Reflection in Tests

Unit tests MAY use `setAccessible(true)` and `@SuppressWarnings` when needed to validate
internal behavior.

### Surefire Configuration

```xml
<includes>
    <include>**/*Test.java</include>
</includes>
<systemPropertyVariables>
    <junit.platform.excluded.engines>paramixel</junit.platform.excluded.engines>
</systemPropertyVariables>
```

The Paramixel engine is excluded during unit tests to prevent it from discovering unit
test classes as `@Paramixel.TestClass` tests.

JaCoCo agent is attached during unit tests for coverage collection.

### Unit Test Template

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

---

## Functional Test Structure (`tests` module)

Functional tests are `@Paramixel.TestClass` classes executed by `paramixel-maven-plugin:test`.
Surefire is disabled for this module.

### What Makes a Good Functional Test

1. **Lifecycle counter assertions** -- use `static AtomicInteger` counters; assert in
   `@Paramixel.Finalize`.
2. **Argument tracking** -- use `ConcurrentSkipListSet<Integer>` for observed argument indices.
3. **Store scoping** -- write in `@Paramixel.BeforeAll`, read in `@Paramixel.Test`, remove
   in `@Paramixel.Finalize`.
4. **Concurrency verification** -- use `ConcurrentHashMap` with argument-indexed entries.

### Test Sub-Packages

| Package | Purpose |
|---|---|
| `test` | Top-level lifecycle and basic argument tests |
| `test.argument` | Per-argument-type tests (null, arrays, collections, enums, etc.) |
| `test.lifecycle` | Lifecycle ordering and inheritance tests |
| `test.named` | `Named`/`NamedValue` display name tests |
| `test.order` | `@Paramixel.Order` annotation tests |
| `test.store` | Store scoping and `AutoCloseable` integration |
| `test.tags` | Tag-based filtering tests |

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

---

## Testcontainers Usage (`examples` module)

Testcontainers is used exclusively in `paramixel-examples` (test scope only). Docker
is required.

### Pattern

1. Create a `TestEnvironment` class implementing `Named` and `AutoCloseable`.
2. Return instances from `@Paramixel.ArgumentsCollector`.
3. In `@Paramixel.BeforeAll`: initialize containers.
4. In `@Paramixel.AfterAll`: destroy via `CleanupExecutor`.
5. Engine auto-calls `close()` after `@Paramixel.AfterAll`.

### `CleanupExecutor` Pattern

```java
new CleanupExecutor()
    .addTask(testEnvironment::destroy)
    .addTaskIfPresent(
        () -> context.getClassContext().getStore().remove("network", Network.class),
        Network::close)
    .throwIfFailed();
```

### Available Container Libraries (test scope)

| Library | Purpose |
|---|---|
| `testcontainers:testcontainers` 2.0.3 | Base Testcontainers |
| `testcontainers:kafka` 1.21.4 | Kafka container |
| `testcontainers:mongodb` 1.21.4 | MongoDB container |
| `testcontainers:nginx` 1.21.4 | nginx container |
| `kafka-clients` 4.2.0 | Kafka producer/consumer |
| `mongodb-driver-sync` 5.6.4 | MongoDB sync driver |

---

## Code Coverage

JaCoCo is configured only in the `engine` module.

| Configuration | Value |
|---|---|
| Plugin version | `jacoco-maven-plugin` 0.8.14 |
| Included packages | `org.paramixel.*` |
| Report phase | `verify` |
| Report location | `engine/target/site/jacoco/index.html` |

No minimum coverage threshold is currently enforced.

---

## Mocking Strategy

No mocking framework is declared. The approach is:

- **Real collaborators preferred.** Tests instantiate real objects.
- **Minimal stubs only when necessary.** Simple anonymous classes or minimal concrete
  implementations for interfaces like `TestEngine` or `EngineExecutionListener`.
- Never mock what you can use for real.

If Mockito is needed in the future, it MUST be added to the relevant `pom.xml` and this
spec MUST be updated.

---

## CI/CD Pipeline

| Workflow | Trigger | Command |
|---|---|---|
| `.github/workflows/build.yaml` | Push to any branch; PR to `main` | `./mvnw -B clean verify` on Ubuntu 24.04, Java 25 Corretto |
| `.github/workflows/manual-build.yaml` | Manual trigger | Same command |

All tests (unit + functional + examples) run in CI. A failing test fails the CI build.

---

## Checklists for New Tests

### Adding a Unit Test

- [ ] Place in `engine/src/test/java/org/paramixel/engine/<package>/`
- [ ] Name: `<ProductionClass>Test.java`
- [ ] Use `@Test` from JUnit Jupiter; use AssertJ assertions
- [ ] Do NOT annotate with `@Paramixel.TestClass`
- [ ] Class MUST be `public`; test methods MUST be `public`
- [ ] Ensure Surefire discovers it (`**/*Test.java`)
- [ ] Run `./mvnw test -pl engine`

### Adding a Functional Test

- [ ] Place in `tests/src/test/java/test/` (or sub-package)
- [ ] Annotate with `@Paramixel.TestClass`
- [ ] Use only `@Paramixel.*` annotations (NOT JUnit Jupiter)
- [ ] Import `org.paramixel.api.*` only (no `org.paramixel.engine.*`)
- [ ] Use AssertJ assertions
- [ ] Run `./mvnw test -pl tests`

### Adding a Testcontainers Example

- [ ] Place in `examples/src/test/java/examples/testcontainers/<service>/`
- [ ] Create `<Service>TestEnvironment.java` implementing `Named` + `AutoCloseable`
- [ ] Create `<Service>Test.java` annotated `@Paramixel.TestClass`
- [ ] Use `CleanupExecutor` in `@Paramixel.AfterAll`
- [ ] Run `./mvnw test -pl examples` (requires Docker)
