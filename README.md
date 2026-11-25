# Paramixel

**Structured test orchestration for Java integration, compatibility, and lifecycle-heavy testing.**

Paramixel's primary unit is an immutable **task definition**. Tasks form an ordered containment tree, optionally with
bodies and subtrees. The compiler derives execution dependencies, resource scopes, ordering constraints, and immutable
execution metadata; the scheduler executes occurrences with bounded concurrency and deterministic admission priorities.

```java
public final class ScenarioTest {

    private static final String ID_START = "start";
    private static final String ID_HEALTH = "health";
    private static final String ID_METRICS = "metrics";
    private static final String ID_STOP = "stop";
    private static final String ENVIRONMENT = "environment";
    private static final String FIXTURE = "fixture";

    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    @Paramixel.TaskFactory
    public static TaskBuilder<?> factory() {
        final var root = Parallel.builder(ScenarioTest.class, "Environments", Pool.named("environments"));
        for (final var environment : environments) {
            root.step(Sequential.builder(environment.name())
                    .bind(ENVIRONMENT, environment)
                    .before(ID_START)
                    .step(Sequential.builder("checks").step(ID_HEALTH).step(ID_METRICS))
                    .after(ID_STOP));
        }
        return root;
    }

    @Paramixel.Id(ID_START)
    public static void start(final Context context) {
        final var fixture = new Scenario(context.require(ENVIRONMENT, Environment.class));
        context.put(FIXTURE, fixture);
        fixture.start();
    }
}
```

Every node is `bind* → before? → step+ → after?`: `bind` declares data that already exists when the plan is built,
`before` and `after` each carry at most one id, task, or builder, and `step` adds children. One class literal per
definition root, one identifier per hook, and ordinary static Java for the behaviour: no functional interfaces, no
reflective fixture lookup, and no unchecked casts. Hooks read what they need from the context by name.

## Requirements

- Java 17 or newer
- Maven 3.9+ (the build enforces it)

## Modules

| Module | Purpose |
| --- | --- |
| `core` | The library: definitions, compiler, scheduler, discovery, reporting |
| `maven-plugin` | Maven `test`-phase integration; goal prefix `paramixel` |
| `examples` | Example and Altcontainers-based integration tests |
| `benchmarks` | JMH benchmarks for compilation and scheduling |

## The model

One sealed definition type, `Task`, implemented by `Sequential`, `Parallel`, and `Repeat`:

- **`Sequential.builder(...)` / `Parallel.builder(...)` / `Repeat.builder(...)`** are the authoring entry points.
  `Parallel` always names a `Pool`; `Repeat` always names a finite `maxIterations`.
- **`dependent()` / `independent()`** describe only *failure continuation* of ordered children, so they exist on
  sequential builders only. `dependent` is the default.
- **`@Paramixel.Id`** names a hook; the tree refers to it by that identifier, which is also the label reported for the
  hook. Hooks must be `public static` and take one `Context` (plus a `Status` for repeat predicates).
- **`bind(name, value)`** declares data that already exists when the plan is built: it is seeded into the node's scope
  before its `before` slot and reported as a plan parameter. `put`/`require`/`find`/`remove` are the runtime store, for
  resources created while the test runs (a started container, a client, a temporary directory). The framework never closes a stored value and never probes for `AutoCloseable` — release is authored in
  `after`, with `find` for partial state.
- **`.step(id)`** adds a body-only child; **`.body(id)`** puts a body on the occurrence itself, which is how a step keeps
  both a body and children.
- **`.before(...)` / `.after(...)`** take an id, a `Task`, or a builder and may be called once each. Several setup or
  cleanup phases compose into one subtree, for example
  `.after(Sequential.builder("cleanup").independent().step(tearDownB).step(tearDownA))`; a `Sequential` cleanup subtree
  is `dependent()` by default, so a failing cleanup step skips the later ones, and `independent()` is how a cleanup that
  must always finish declares itself.
- **`repeat`, `retry`, `timeout`, `isolated`, `tags`, `shuffle`, `onlyIf(id)`** are declarations on the definition,
  validated before anything runs.

Building a definition, compiling a plan, and discovering factories are all side-effect free: no hook, body, or runtime
predicate runs until the scheduler admits an occurrence.

## Pools, workers, and cancellation

A `Pool` is a logical concurrency budget, never a thread pool. Capacity comes from `Runner.Builder.pool(Pool, int)` or
`paramixel.parallelism.<name>`, with the builder winning.

- Each direct child of a parallel task holds one permit from admission through its body, children, and `after` hooks.
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

`@Paramixel.TaskFactory` methods must be `public static`, take no parameters or exactly one `Configuration`, and return
a `Task` or a builder, rooted at the factory's own class so its identifiers resolve. The `Configuration` parameter is the
run's effective configuration, so configuration can decide the *shape* of the definition — which environments to author,
which branches to include — rather than only what its tasks do at execution time. Returning `null` is an explicit skip.
`@BeforeAll` / `@AfterAll` wrap the discovered run, `@Disabled` excludes a factory, `@Tag` and `Selector` select, and
`@Priority` orders.

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
| `paramixel.failFast` | stop admitting new root tasks after a root task fails (skips and aborts do not count) | `false` |
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
