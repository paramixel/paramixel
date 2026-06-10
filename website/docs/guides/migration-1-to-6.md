---
title: Migration 1.x to 6.0.0
description: Upgrade plan for moving Paramixel 1.x projects to 6.0.0.
---

# Migration 1.x to 6.0.0

Use this plan when upgrading a Paramixel 1.x project directly to 6.0.0.

## Migration focus

- Replace legacy package imports and factory annotations with `org.paramixel.api` and `@Paramixel.Factory`.
- Rebuild tests as explicit `Action` trees using `Builder` subtypes.
- Move runtime state access into `Context` fixture lookup through `Instance` actions.

## 6.0.0 target checkpoints

- Use Java 17+ and imports from `org.paramixel.api`, `org.paramixel.api.action`, `org.paramixel.api.selector`, and `org.paramixel.api.support`.
- Define discovery entry points as public static methods annotated with `@Paramixel.Factory`.
- Return `Action`, `Builder`, or `null` from factories; `null` produces a skipped outcome.
- Build action trees with `Sequential`, `Parallel`, `Scope`, `Instance`, `Repeat`, `Timeout`, `Step`, and `Assert` (`Static` is deprecated since 6.2 — use `Scope` instead).
- Read execution state from `Result.descriptor()` and the descriptor tree.
- Configure runs with `paramixel.properties`, JVM system properties, or programmatic `Configuration` objects.
- Use `paramixel.match.package.regex`, `paramixel.match.class.regex`, and `paramixel.match.tag.regex` for discovery filtering.
- Use `paramixel.report.file` to enable `.json`, `.xml`, `.html`, `.log`, or `.txt` reports.

## Recommended path

1. Update dependencies to the 6.0.0 core and Maven plugin artifacts.
2. Replace legacy imports, annotations, and action builders with the 6.0.0 API.
3. Move configuration to `paramixel.properties`, JVM `-D` properties, or programmatic `Configuration` objects.
4. Run the Paramixel examples or your factory package with `mvn paramixel:test`, `mvn test`, or a Gradle `JavaExec` task that invokes `org.paramixel.api.Runner`.
5. Enable `paramixel.report.file` during validation so the descriptor tree and aggregate outcome are visible.

## Minimal 6.0.0 factory

```java
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

public final class SmokeTest {
    @Paramixel.Factory
    @Paramixel.Tag("smoke")
    public static Action smoke() {
        return Sequential.sequential("smoke")
                .child(Step.of("arrange", ctx -> arrange()))
                .child(Step.of("act", ctx -> act()))
                .child(Step.of("assert", ctx -> assertResult()))
                .build();
    }
}
```
