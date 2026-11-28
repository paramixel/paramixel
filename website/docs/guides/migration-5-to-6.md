---
title: Migration 5.0.x to 6.0.0
description: Upgrade plan for moving Paramixel 5.0.x projects to 6.0.0.
---

# Migration 5.0.x to 6.0.0

Use this plan when upgrading a Paramixel 5.0.x project directly to 6.0.0.

> **Historical guide.** This page records the upgrade path to Paramixel 6.x, whose action API is documented in the
> versioned 6.x documentation. The sample at the end uses the current 7.0 authoring surface; see
> [Migrating from 6.x to 7.0](migration-6-to-7.md) for the differences.

## Migration focus

- Keep the action-tree model and verify imports match the 6.0.0 public packages.
- Replace `Spec` with `Action`; replace `Sequential.of()` with `sequential()`.
- Use `Context` for fixture/configuration access and `Descriptor` for execution state.
- Adopt 6.0.0 configuration keys, especially `paramixel.listener.exclude` for listener output suppression.

## 6.0.0 target checkpoints

- Use Java 17+ and imports from `org.paramixel.api`, `org.paramixel.api.action`, `org.paramixel.api.selector`, and `org.paramixel.api.support`.
- Define discovery entry points as public static methods annotated with `@Paramixel.TaskFactory`.
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

## Minimal current factory

The 6.x factory returned an `Action`; the current surface returns a `Task` or a `TaskBuilder` and names node kinds by
type:

```java
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Sequential;
import org.paramixel.api.SequentialBuilder;

public final class SmokeTest {

    @Paramixel.TaskFactory
    @Paramixel.Tag("smoke")
    public static SequentialBuilder smoke() {
        return Sequential.builder(SmokeTest.class, "smoke").step("arrange").step("act").step("assert");
    }

    @Paramixel.Id("arrange")
    public static void arrange(final Context context) {}

    @Paramixel.Id("act")
    public static void act(final Context context) {}

    @Paramixel.Id("assert")
    public static void assertResult(final Context context) {}
}
```

See [Migrating from 6.x to 7.0](migration-6-to-7.md) for the full mapping.
