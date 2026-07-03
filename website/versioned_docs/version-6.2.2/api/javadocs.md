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

The Gradle build also enforces Javadoc quality for core sources:

```bash
./mvnw spotless:apply
./gradlew javadoc --no-daemon
```

## Public packages

- `org.paramixel.api`
- `org.paramixel.api.action`
- `org.paramixel.api.exception`
- `org.paramixel.api.selector`
- `org.paramixel.api.support`

Internal implementation packages under `nonapi.org.paramixel` are not public API.
