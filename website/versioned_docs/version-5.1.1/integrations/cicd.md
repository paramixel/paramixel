---
title: CI/CD
description: Running Paramixel tests in continuous integration pipelines.
---

# CI/CD

Paramixel tests integrate naturally with CI/CD pipelines. The key integration point is the process exit code: `0` for success, `1` for failure.

## GitHub Actions

```yaml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
          distribution: temurin
      - name: Run tests
        run: ./mvnw test
      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: paramixel-report-java-${{ matrix.java }}
          path: target/paramixel/
```

## Gradle on CI

```yaml
- name: Run Paramixel tests
  run: ./gradlew paramixelTest -Dparamixel.report.file=build/paramixel/report.json
- name: Upload report
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: paramixel-report
    path: build/paramixel/
```

## Exit code behavior

`Runner.runAndReturnExitCode()` returns:

| Condition | Exit code |
| --- | --- |
| `PASSED` | 0 |
| `SKIPPED` | 0 (or 1 if `failureOnSkip=true`) |
| `ABORTED` | 1 (or 0 if `failureOnAbort=false`) |
| `FAILED` | 1 |
| `PENDING` | 1 |

## Docker-based tests

Paramixel works with Testcontainers for Docker-based integration testing. Ensure Docker is available in the CI environment:

```yaml
services:
  docker:
    image: docker:dind
    options: --privileged
```

## Fail when no tests are discovered

In CI, empty test discovery should fail the pipeline:

```bash
# Maven
./mvnw test -Dparamixel.failIfNoTests=true

# Gradle
./gradlew paramixelTest -Dparamixel.failIfNoTests=true
```

## Parallelism on CI

Set parallelism based on available CPUs:

```bash
# Maven
./mvnw test -Dparamixel.parallelism=4

# Gradle
./gradlew paramixelTest -Dparamixel.parallelism=4
```

The default is `Runtime.getRuntime().availableProcessors()`, which may be high on CI runners with limited CPU. Setting an explicit value prevents oversubscription.

## Report artifacts

Configure a report file so CI can archive results:

```bash
# Maven
./mvnw test -Dparamixel.report.file=target/paramixel/report.json

# Gradle
./gradlew paramixelTest -Dparamixel.report.file=build/paramixel/report.json
```

Report formats: `.json`, `.xml`, `.html`, `.log`/`.txt`.

## Next steps

- [Maven Plugin](./maven-plugin)
- [Gradle Integration](./gradle)
- [Reporting](./reporting)
