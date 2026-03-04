# Paramixel — System Overview

## Project Purpose

Paramixel is a custom JUnit Platform test engine designed for **parallel, parameterized test execution with comprehensive lifecycle management**. It enables data-driven tests by pairing each argument produced by a `@Paramixel.ArgumentsCollector` method with every `@Paramixel.Test` method in a class, executing them concurrently under a configurable concurrency model backed by Java 21 virtual threads. The project also ships a Maven plugin so the engine integrates transparently into the standard Maven `test` phase.

---

## Module Inventory

| Module name (artifactId) | Packaging | Responsibility | Depends on |
|---|---|---|---|
| `paramixel-api` | `jar` | Public annotation and context API consumed by test authors | `junit-platform-commons` |
| `paramixel-engine` | `jar` | JUnit Platform `TestEngine` implementation: discovery, scheduling, execution | `paramixel-api`, `junit-platform-engine`, `junit-platform-launcher`, `junit-jupiter`, `slf4j-api`, `ascii-table` |
| `paramixel-maven-plugin` | `maven-plugin` | Maven Mojo that discovers `@Paramixel.TestClass` classes and fires the engine via the JUnit Platform Launcher | `paramixel-api`, `paramixel-engine`, `junit-platform-launcher`, `maven-plugin-api`, `maven-core`, `maven-artifact`, `maven-resolver-impl` |
| `paramixel-tests` | `jar` | Functional/integration tests that exercise the engine using the `paramixel-maven-plugin` goal | `paramixel-api` (test), `paramixel-engine` (test) |
| `paramixel-examples` | `jar` | Demonstrative test classes showing API usage, including Testcontainers-based integration examples | `paramixel-api` (test), `paramixel-engine` (test), `testcontainers`, `kafka-clients`, `mongodb-driver-sync` |
| `paramixel-benchmarks` | `jar` | Performance benchmarks using JMH to measure engine throughput, latency, and compare with JUnit Jupiter | `paramixel-api` (test), `paramixel-engine` (test), `jmh-core`, `junit-jupiter` (for comparison benchmarks) |

### Module Dependency Graph

```
paramixel-api
    ↑
paramixel-engine ──────────────────┐
    ↑                               │
paramixel-maven-plugin             │
    ↑                               ↓
paramixel-tests ←── (uses plugin) ──┤
paramixel-examples ←── (uses plugin)│
paramixel-benchmarks ←──────────────┘
```

---

## Technology Stack

| Concern | Technology / Version |
|---|---|
| Language | Java 21 (compiled with `--release 21`; CI uses Java 25 Corretto) |
| Build tool | Apache Maven 3.9+ with `./mvnw` wrapper |
| Test platform | JUnit Platform 6.0.3 (engine/launcher/commons) |
| Assertions | AssertJ 3.27.7 |
| Logging (engine) | `java.util.logging` (JUL) + SLF4J API 2.0.17 |
| Concurrency | Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) |
| Code formatting | Spotless + Palantir Java Format 2.87.0 (applied at compile phase) |
| Code coverage | JaCoCo 0.8.14 (engine module only) |
| Version management | `versions-maven-plugin` 2.18.0 with `versions-rules.xml` |
| Build enforcement | `maven-enforcer-plugin` — requires Maven ≥ 3.9.0 |
| Table output | `ascii-table` 1.9.0 (engine execution listener) |
| Null safety annotations | `org.jspecify` (`@NonNull`) |
| Integration examples | Testcontainers 2.0.3 / 1.21.4 (Kafka, MongoDB, nginx, bufstream/tansu) |

---

## Repository Layout Map

```
paramixel/
├── api/                    Public API jar — annotations + context interfaces
│   └── src/main/java/org/paramixel/api/
├── engine/                 Core test engine jar
│   ├── src/main/java/org/paramixel/engine/
│   │   ├── api/            Concrete context/store implementations
│   │   ├── descriptor/     JUnit Platform TestDescriptor hierarchy
│   │   ├── discovery/      Test class scanner (ParamixelDiscovery)
│   │   ├── execution/      Runners + concurrency primitives
│   │   ├── invoker/        Reflective method invoker
│   │   ├── listener/       Execution event listeners / reporters
│   │   ├── util/           FastId generator
│   │   └── validation/     Method signature validator
│   └── src/main/resources/
│       ├── META-INF/services/org.junit.platform.engine.TestEngine  (SPI registration)
│       └── paramixel.properties  (version, Maven-filtered)
├── maven-plugin/           Maven Mojo (goal: test)
│   └── src/main/java/org/paramixel/maven/plugin/
├── tests/                  Functional test suite — executed via paramixel-maven-plugin
│   └── src/test/java/test/
├── examples/               Illustrative tests including Testcontainers scenarios
│   └── src/test/java/examples/
├── assets/
│   └── license-header.txt  Apache 2.0 license header for Spotless
├── .github/workflows/
│   ├── build.yaml          CI: push + PR → ./mvnw -B clean verify
│   └── manual-build.yaml   Manual trigger build
├── versions-rules.xml      Dependency version exclusions for versions-maven-plugin
├── paramixel.properties    Project-root properties (loaded by engine at runtime)
├── mvnw / .mvn/            Maven wrapper
└── pom.xml                 Parent POM (groupId: org.paramixel, version: 0.0.1)
```
