# Paramixel

**Structured test orchestration for Java integration, compatibility, and lifecycle-heavy testing.**

Paramixel's primary unit is an immutable **task definition**. Tasks form an ordered containment tree, optionally with
bodies and subtrees. The compiler derives execution dependencies, resource scopes, ordering constraints, and immutable
execution metadata; the scheduler executes occurrences with bounded concurrency and deterministic admission priorities.

```java
import static org.paramixel.api.Tasks.*;

Task definition = parallel("Environments", Pool.named("environments"))
        .each(environments, environment -> forInstance(environment.name(), () -> new Scenario(environment))
                .before(Scenario::start)
                .sequential("checks", checks -> checks
                        .step("health", Scenario::checkHealth)
                        .step("metrics", Scenario::checkMetrics))
                .after(Scenario::stop))
        .build();

try (Runner runner = Runner.builder()
        .pool(Pool.named("environments"), 3)
        .build()) {
    Result result = runner.run(definition);
    System.exit(result.exitCode());
}
```

No class tokens on steps, no reflective fixture lookup, no unchecked casts. `T` is inferred from the supplier and carried
through nested groups.

## Requirements

- Java 17 or newer
- Maven 3.9+ (the build enforces it)

## Modules

| Module | Purpose |
| --- | --- |
| `core` | The library: definitions, compiler, scheduler, discovery, reporting |
| `maven-plugin` | Maven `test`-phase integration; goal prefix `paramixel` |
| `examples` | Example and Testcontainers-based integration tests |
| `benchmarks` | JMH benchmarks for compilation and scheduling |

## The model

One immutable definition type, `Task`:

- **`sequential` / `parallel`** describe how a task's direct children run. `parallel` always names a `Pool`.
- **`dependent()` / `independent()`** describe only *failure continuation* of ordered children, so they exist on
  sequential builders only. `dependent` is the default.
- **`forInstance(name, supplier)`** owns one lazily created instance per execution attempt. Lifecycle is `before` /
  `after`; there is no separate instantiate/destroy ceremony and no implicit `AutoCloseable` closing.
- **`step(name, operation, children -> ...)`** attaches a subtree explicitly. There is no hidden "current step" cursor.
- **`each(items, mapper)`** expands eagerly at definition time and may produce builders bound to different instance
  types.
- **`when`, `repeat`, `retry`, `timeout`, `isolated`, `tags`, `shuffle`, `resource`** are typed declarations on the
  definition, validated before anything runs.

Building a definition, compiling a plan, and discovering factories are all side-effect free: no instance factory,
lifecycle callback, body, or runtime predicate runs until the scheduler admits an occurrence.

## Pools, workers, and cancellation

A `Pool` is a logical concurrency budget, never a thread pool. Capacity comes from `Runner.Builder.pool(Pool, int)` or
`paramixel.parallelism.<name>`, with the builder winning.

- Each direct child of a parallel task holds one permit from admission through instance creation, body, children,
  `after`, and resource release.
- Waiting for a permit or a lock never occupies a worker thread.
- Nested use of the same pool on one ancestry path, undefined pools, and provable acquisition-order cycles are rejected
  before execution.
- A timeout is cooperative: the running callback is interrupted, no new work is admitted in that subtree, and finalizers
  run only after quiescence. A callback that ignores interruption produces an explicitly **non-quiescent** result with
  withheld cleanup — Java cannot forcibly stop arbitrary code, and Paramixel does not claim otherwise.

## Statuses and exit codes

`PASSED`, `FAILED`, `SKIPPED`, `ABORTED` (assumption), and `CANCELED` are distinct. Aggregation is
`FAILED > CANCELED > ABORTED > SKIPPED > PASSED`, so a partially executed group stays visible instead of looking like a
pass. `Result.status()` is the semantic outcome; `Result.exitCode()` applies `paramixel.failureOnSkip`,
`paramixel.failureOnAbort`, and `paramixel.failIfNoTests` (all defaulting to fail).

## Discovery

`@Paramixel.Factory` methods must be `public static`, take no parameters, and return a `Task` or a builder. Returning
`null` is an explicit skip. `@BeforeAll` / `@AfterAll` wrap the discovered run, `@Disabled` excludes a factory, `@Tag`
and `Selector` select, and `@Priority` orders.

`Runner.run(Task)` executes exactly the definition you supply: no classpath scan and no hooks. `Runner.run()`
discovers. `Launcher` owns `System.exit`.

## Configuration

Classpath `paramixel.properties`, then JVM system properties, then built-in defaults; a runner may override pool
capacities explicitly. The main keys:

| Key | Meaning | Default |
| --- | --- | --- |
| `paramixel.parallelism` | admission budget for discovered root tasks | processors |
| `paramixel.parallelism.<pool>` | capacity of a named pool | none (must be set) |
| `paramixel.workers` | execution worker threads | processors |
| `paramixel.shutdown.graceMillis` | bounded wait before reporting non-quiescence | `10000` |
| `paramixel.failureOnSkip` / `paramixel.failureOnAbort` | exit-code mapping | `true` / `true` |
| `paramixel.failIfNoTests` | fail when discovery finds nothing | `true` |
| `paramixel.failFast` | stop admitting new root tasks after a root failure | `false` |
| `paramixel.report.file` | report file; format follows the extension | none |

An unknown `paramixel.*` key is reported as a warning rather than silently ignored.

## Build and test

```bash
./mvnw spotless:apply          # format before any validation
./mvnw clean verify            # full validation: tests, Javadoc, Spotless, PMD
./mvnw test -Dparamixel.skipTests   # core unit tests only
./mvnw test -pl examples       # example integration tests (requires Docker)
```

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICES](NOTICES).
