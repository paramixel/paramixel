---
title: Reporting
description: Built-in report generation in Paramixel.
---

# Reporting

Paramixel includes built-in report listeners that write execution summaries to files. Reports are configured via the `paramixel.report.file` configuration key.

## Enable reporting

### Maven

```bash
./mvnw test -Dparamixel.report.file=target/paramixel/report.json
```

Or in the POM:

```xml
<configuration>
    <reportFile>${project.build.directory}/paramixel/report.json</reportFile>
</configuration>
```

### Gradle

```bash
./gradlew paramixelTest -Dparamixel.report.file=build/paramixel/report.json
```

Or in the build file:

```groovy
tasks.register('paramixelTest', JavaExec) {
    systemProperty('paramixel.report.file', layout.buildDirectory.file('paramixel/report.json').get().asFile.absolutePath)
}
```

## Report formats

The format is inferred from the file extension:

| Extension | Format | Description |
| --- | --- | --- |
| `.json` | JSON | Structured JSON report with tree hierarchy |
| `.xml` | XML | XML report with tree hierarchy |
| `.html`, `.htm` | HTML | Self-contained HTML report |
| `.log`, `.txt`, or other | Text | Plain text tree summary |

## Report content

Reports contain the summary tree and footer. They do not include per-action status lines or stack traces from the console output.

The report file is overwritten on each run. Parent directories are created on demand.

## Tilde expansion

On Linux and macOS, `~` in the report file path expands to the current user's home directory:

```properties
paramixel.report.file=~/paramixel-reports/report.json
```

On Windows, tilde expansion is a no-op and the path is used as-is.

## Console output

Console output always includes the tree summary regardless of report file configuration. ANSI color output is controlled by `paramixel.ansi`:

```bash
# Force ANSI off (useful for CI logs)
./mvnw test -Dparamixel.ansi=false

# Force ANSI on
./mvnw test -Dparamixel.ansi=true
```

## Listener chain

The default listener chain combines:

1. **StatusListener** — prints per-action status lines to the console
2. **SummaryListener** — prints the tree summary and run footer
3. **ReportListener** (optional) — writes the report file when `paramixel.report.file` is configured

All listeners are wrapped in `SafeListener` so non-`Error` throwables from individual callbacks are reported rather than aborting the run.

## Custom listeners

Implement `Listener` to receive lifecycle callbacks:

```java
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.action.Descriptor;

public class CustomListener implements Listener {

    @Override
    public void onRunStarted() {
        System.out.println("Run started");
    }

    @Override
    public void onDiscoveryCompleted(Descriptor root) {
        System.out.println("Discovery completed: " + root.metadata().name());
    }

    @Override
    public void onAfterExecution(Descriptor descriptor) {
        System.out.println("Action completed: " + descriptor.metadata().name());
    }

    @Override
    public void onRunCompleted(Result result) {
        System.out.println("Run completed: " + result.status());
    }
}
```

Register with the runner:

```java
Runner runner = Runner.builder()
        .listener(new CustomListener())
        .build();
```

## Next steps

- [Maven Plugin](./maven-plugin)
- [Gradle Integration](./gradle)
- [CI/CD](./cicd)
