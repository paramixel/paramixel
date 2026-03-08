# Paramixel -- Module Boundaries

This spec defines what each module is allowed to do and what it MUST NOT do. For the
module inventory and dependency graph, see `01-overview.md`.

---

## `paramixel-api`

**Responsibility:** Defines the public contract between test authors and the engine.
Contains only annotations, interfaces, and one concrete value class. Zero engine
implementation code.

### Package Structure

```
org.paramixel.api
+-- Paramixel.java              All lifecycle annotations as nested types
+-- ArgumentContext.java         Interface: per-invocation context
+-- ArgumentsCollector.java     Interface: argument registration + parallelism
+-- ClassContext.java            Interface: per-class context
+-- EngineContext.java           Interface: engine-level config context
+-- Named.java                  Interface: argument display name provider
+-- NamedValue.java             Concrete: generic name+value wrapper
+-- Store.java                  Interface: scoped key-value store
```

### Constraints

- MUST NOT contain engine implementation code.
- MUST NOT depend on `paramixel-engine` or `paramixel-maven-plugin`.
- MUST NOT use JUnit Jupiter, Spring, or CDI annotations.
- MUST NOT contain persistence, HTTP, or messaging code.
- MUST NOT introduce IoC container annotations.
- Has no runtime configuration of its own.

---

## `paramixel-engine`

**Responsibility:** Implements the JUnit Platform `TestEngine` SPI: discovers test classes,
validates method signatures, builds the descriptor tree, and executes tests using virtual
threads with a concurrency-limited executor.

### Package Structure

```
org.paramixel.engine
+-- ParamixelTestEngine.java
+-- api/            Concrete context/store implementations
+-- configuration/  Engine configuration resolution
+-- descriptor/     JUnit Platform TestDescriptor hierarchy
+-- discovery/      Test class scanner
+-- execution/      Runners + concurrency primitives
+-- filter/         Tag-based test filtering
+-- invoker/        Reflective method invoker
+-- listener/       Execution event listeners / reporters
+-- util/           FastIdUtil generator
+-- validation/     Method signature validator
```

### Constraints

- MUST NOT expose API types for test author import (those live in `paramixel-api`).
- MUST NOT depend on Spring, Guice, or any IoC container.
- MUST NOT perform file I/O outside loading `paramixel.properties` and scanning classpath.
- MUST NOT depend on `paramixel-maven-plugin`.
- MUST NOT use HTTP clients or messaging clients.
- MUST NOT store persistent state between test runs.
- **Production source (`src/main/java`)** MUST NOT contain `@Paramixel.TestClass`-annotated classes.
- **Test source (`src/test/java`)** MAY contain `@Paramixel.TestClass`-annotated classes when used as
  test data for JUnit Jupiter unit tests (e.g., testing discovery or validation logic).
  These are internal test helpers, not functional tests.

---

## `paramixel-maven-plugin`

**Responsibility:** Bridges the Maven build lifecycle to the Paramixel engine. Single goal
(`test`) that discovers `@Paramixel.TestClass` classes and fires the JUnit Platform Launcher.

### Package Structure

```
org.paramixel.maven.plugin
+-- ParamixelMojo.java
```

### Constraints

- MUST NOT implement test execution logic (that belongs in `paramixel-engine`).
- MUST NOT bypass the JUnit Platform Launcher.
- MUST NOT contain `@Paramixel.TestClass` annotated classes.
- MUST NOT depend on engine-internal packages (`org.paramixel.engine.*`), only on
  `paramixel-api` and `paramixel-engine` as opaque dependencies.
- MUST NOT introduce HTTP, persistence, or messaging dependencies.

---

## `paramixel-tests`

**Responsibility:** Functional/integration test suite validating the engine's lifecycle,
argument handling, concurrency, store scoping, and ordering. Tests are `@Paramixel.TestClass`
classes executed by `paramixel-maven-plugin`. Surefire is disabled.

### Package Structure

```
test/
+-- BasicTest.java, DisabledTest.java, ...
+-- argument/       Per-argument-type tests
+-- lifecycle/      Lifecycle ordering and inheritance tests
+-- named/          Named value display name tests
+-- order/          @Paramixel.Order annotation tests
+-- store/          Store scoping and AutoCloseable tests
+-- tags/           Tag-based filtering tests
```

### Constraints

- MUST NOT contain production engine code.
- MUST NOT use JUnit Jupiter `@Test` or `@ParameterizedTest`.
- MUST NOT depend on `org.paramixel.engine.*` implementation classes (only `paramixel-api`
  and `paramixel-engine` jar).
- MUST NOT add Testcontainers or external service dependencies.

---

## `paramixel-examples`

**Responsibility:** Demonstrates the Paramixel API with realistic usage patterns including
Testcontainers integration. Executed by `paramixel-maven-plugin`. Surefire is disabled.

### Package Structure

```
examples/
+-- simple/         Sequential and parallel argument examples
+-- complex/        Complex scenario examples
+-- support/        Logger, Resource, CleanupExecutor utilities
+-- testcontainers/ Kafka, MongoDB, nginx, bufstream, tansu examples
```

### Constraints

- MUST NOT contain engine unit tests.
- MUST NOT add dependencies consumed by `paramixel-api` or `paramixel-engine`.
- MUST NOT reference engine-internal packages.
- All Testcontainers usage MUST be `test` scope only.

---

## `paramixel-benchmarks`

**Responsibility:** JMH performance benchmarks measuring engine throughput, latency, and
comparison with JUnit Jupiter.

### Package Structure

```
benchmarks/src/main/java/org/paramixel/engine/
+-- api/       Benchmarks for api-layer implementations
+-- filter/    Benchmarks for tag filtering
+-- util/      Benchmarks for utility classes
```

### Key Classes

| Class | Role |
|---|---|
| `ConcreteStoreBenchmark` | Microbenchmarks for `ConcreteStore` operations |
| `FastIdBenchmark` | Microbenchmarks for `FastIdUtil` ID generation |
| `RegexTagFilterBenchmark` | Microbenchmarks for tag filter regex matching |

### Constraints

- MUST NOT contain functional or integration tests.
- MUST NOT depend on `paramixel-maven-plugin`.
- MUST NOT run during standard Maven build phases (requires `-Pbenchmarks` profile).
- MUST NOT be executed in CI by default.
- MAY declare synthetic `@Paramixel.TestClass` types as benchmark inputs (not discovered
  during normal builds).

### Execution

```bash
# Run all benchmarks
./mvnw test -pl benchmarks -Pbenchmarks

# Run specific benchmark
./mvnw test -pl benchmarks -Pbenchmarks -Dbenchmarks.class=FastIdBenchmark

# Custom settings
./mvnw test -pl benchmarks -Pbenchmarks \
    -Dbenchmarks.forks=3 \
    -Dbenchmarks.measurement.iterations=20
```

Results: `benchmarks/target/jmh-results.json` and `benchmarks/target/jmh-results.txt`.
