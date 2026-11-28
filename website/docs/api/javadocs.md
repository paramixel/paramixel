---
title: Javadocs
description: Build the authoritative Paramixel API reference.
---

# Javadocs

The generated Javadocs are the authoritative low-level API reference.

## Build locally

From the repository root:

```bash
./mvnw spotless:apply
./mvnw javadoc:javadoc
```

The Maven build is strict: `maven-javadoc-plugin` runs with `doclint=all` and `-Werror`, so a Javadoc warning fails
`./mvnw package` and `./mvnw clean verify`. The Gradle build produces Javadoc for the same exported packages, but it is
not strict: it processes the internal sources and reports their warnings without failing:

```bash
./gradlew :core:javadoc --no-daemon
```

## Public packages

- `org.paramixel.api`
- `org.paramixel.api.exception`
- `org.paramixel.api.selector`
- `org.paramixel.api.support`

Internal implementation packages under `nonapi.org.paramixel` are not public API. Because `core` is a named module that
exports only the API packages, they are omitted from the published Javadoc; the vendored ClassGraph packages are
excluded explicitly as well.
