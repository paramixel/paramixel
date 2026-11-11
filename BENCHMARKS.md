# Benchmarks

JMH benchmarks for Paramixel compilation, definition-traversal, and scheduler performance regression testing.

## Prerequisites

- **Java 17+**
- **Maven 3.9+**

## Build

```bash
./mvnw package -pl benchmarks -DskipTests -Dparamixel.skipTests
```

Creates a fat JAR at `benchmarks/target/benchmarks.jar`.

## Run

### All benchmarks

```bash
java -jar benchmarks/target/benchmarks.jar
```

### Specific benchmark

```bash
java -jar benchmarks/target/benchmarks.jar SchedulerBenchmark
```

### Override parameters

```bash
java -jar benchmarks/target/benchmarks.jar SchedulerBenchmark -p childCount=1000
java -jar benchmarks/target/benchmarks.jar CompilePlanBenchmark -p size=100,1000
```

### Quick mode

```bash
java -jar benchmarks/target/benchmarks.jar -f 1 -wi 3 -i 5
```

### GC allocation profiling

```bash
java -jar benchmarks/target/benchmarks.jar SchedulerBenchmark -prof gc
```

## Available Benchmarks

| Benchmark | Parameters | Description |
|---|---|---|
| `SchedulerBenchmark.runParallelHeterogeneousDurations` | `parallelism`=4,8,16; `childCount`=8,32,128 | Mixed fast/slow children |
| `SchedulerBenchmark.runParallelUniformDurations` | `parallelism`=4,8,16; `childCount`=8,32,128 | Equal-duration children |
| `SchedulerBenchmark.scheduleManySmallTasks` | `childCount`=100,1000,10000 | Trivial steps: scheduling overhead |
| `CompilePlanBenchmark.compilePlan` | `size`=10,100,1000 | Plan compilation throughput |
| `CompilePlanBenchmark.buildTree` | `size`=10,100,1000 | Definition snapshot throughput |
| `ChildrenAccessBenchmark.accessChildren` | `size`=10,100,1000 | Definition traversal |
| `StatusAccumulatorBenchmark.aggregate` | `size`=10,100,1000 | Severity aggregation |
| `FormatIdPathBenchmark.scanIdentities` | `size`=10,100,1000 | Occurrence identity reads |
| `StatusQueryBenchmark.queryConstants` / `queryDynamic` | `queryCount`=10000,100000 | Status query cost |

## Notes

- Benchmarks are excluded from `mvn test` and `mvn verify` (Surefire skipped)
- Benchmarks read no configuration from the environment; each runner is built with an explicit pool so results are reproducible
- The `benchmarks` module is not deployed to Maven Central
- Rebuild the fat JAR after any code changes in `core/`

---

Copyright (c) 2026-present Douglas Hoard

