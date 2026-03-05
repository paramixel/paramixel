---
description: Run a full clean verify build (including benchmarks profile)
---

# Build

Run a full clean build + tests with benchmarks enabled, and capture all output in `output.log`.

## Command

```bash
./mvnw clean verify -Pbenchmarks -Dbenchmarks.enabled=true > output.log 2>&1
```
