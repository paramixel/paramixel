# Benchmarks

JMH benchmarks for Paramixel scheduler performance regression testing.

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
java -jar benchmarks/target/benchmarks.jar SchedulerBenchmark -p size=100,1000
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
| `runParallelHeterogeneousDurations` | `parallelism`=4,8,16; `childCount`=8,32,128 | Mixed fast/slow children — tests rolling window efficiency |
| `scheduleManySmallTasks` | `parallelism`=4,8; `taskCount`=256,1024 | Trivial actions — tests scheduling overhead |
| `runParallelUniformDurations` | `parallelism`=4,8,16; `childCount`=32,64,128 | Equal-duration children — baseline, no regression |

## Notes

- Benchmarks are excluded from `mvn test` and `mvn verify` (Surefire skipped)
- The `benchmarks` module is not deployed to Maven Central
- Rebuild the fat JAR after any code changes in `core/`

---

Copyright (c) 2026-present Douglas Hoard

