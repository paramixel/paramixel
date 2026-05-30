---
title: Javadocs
description: Paramixel API Javadoc reference.
---

# Javadocs

Paramixel publishes Javadocs for every release. Javadocs are the authoritative reference for every public type, method, and annotation in the framework.

## Building Javadocs locally

### Maven

```bash
./mvnw javadoc:javadoc
```

Output is written to `core/target/site/apidocs/`.

### Gradle

```bash
./gradlew javadoc --no-daemon
```

Output is written to `core/build/docs/javadoc/`.

## Javadoc quality

Paramixel enforces strict Javadoc:

- Maven: `doclint:all` + `-Werror` on `maven-javadoc-plugin`
- Gradle: `Xdoclint:all` + `Werror` on the `javadoc` task
- Missing `@param`, `@return`, or `@throws` tags will fail the build
- Record compact constructors require their own `@param` tags

The `examples/` module skips Javadoc generation entirely (`maven.javadoc.skip=true`).

## Public packages

Javadocs cover all public packages:

| Package | Contents |
| --- | --- |
| `org.paramixel.api` | Runner, Configuration, Listener, Result, Status, Descriptor, Paramixel (annotations), AnnotationResolver, Version, ThrowingRunnable, ThrowingConsumer |
| `org.paramixel.api.action` | Action, Spec, Step, Lifecycle, Sequential, Parallel, Instance, Static, Repeat, Timeout, Delay, AssertTrue, AssertFalse, Metadata, Context, Mode |
| `org.paramixel.api.selector` | Selector, AndSelector, OrSelector, NotSelector, RegexSelector, PackageRegexSelector, ClassRegexSelector, TagRegexSelector |
| `org.paramixel.api.exception` | FailException, SkipException, AbortedException, ConfigurationException, CycleDetectedException, ResolverException, PolicyException |
| `org.paramixel.api.support` | Retry, Retry.Policy, Retry.Result, CleanUp |

## Next steps

- [API Overview](./intro)
- [Status](./status)
- [Action](./action)
