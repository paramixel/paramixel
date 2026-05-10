---
title: Reporting
description: Configure summary reports in text, JSON, XML, and HTML formats.
---

# Reporting

Paramixel can write per-run summary reports to a file in several formats.

## Configuration

Set the report file path. The report format is inferred from the file extension:

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

Or via system properties:

```bash
./mvnw test -Dparamixel.report.file=target/paramixel/paramixel.json
./gradlew paramixelTest -Pparamixel.report.file=build/paramixel/paramixel.json
```

The format is inferred from the file extension:

| Extension | Format |
|---|---|
| `.log`, `.txt` | `text` |
| `.json` | `json` |
| `.xml` | `xml` |
| `.html` | `html` |
| other / missing | `text` (default) |

Deprecated compatibility: `paramixel.report.format` is still accepted when present and nonblank, but emits a warning. Prefer selecting the format with the report file extension.

## Supported formats

### Text

Plain-text tree summary using `TreeSummaryRenderer`. Matches the console output format without ANSI color codes.

```properties
paramixel.report.file=target/paramixel/paramixel.log
```

### JSON

Structured JSON output. Each result node contains `name`, `kind`, `status`, `runDuration`, `message`, `exception`, and `children`. Time values are in whole milliseconds.

```properties
paramixel.report.file=target/paramixel/paramixel.json
```

### XML

Element-based XML report. Each result element has attributes for `name`, `kind`, `status`, and `runDuration`, with optional `<message>` and `<exception>` child elements.

```properties
paramixel.report.file=target/paramixel/paramixel.xml
```

### HTML

Self-contained HTML report with an interactive tree view. Features include:

- expand/collapse all nodes
- search filtering
- automatic expansion of failure nodes
- embedded styles, scripts, and result data in a single file

```properties
paramixel.report.file=target/paramixel/paramixel.html
```

## File behavior

- The configured file is overwritten on each run
- Parent directories are created on demand
- Console output still appears normally when a report file is configured
- If the report file cannot be created, Paramixel prints a warning to `System.err` and continues the run
- Tilde (`~`) expansion is supported on Linux and macOS: `~` expands to the current user's home directory, `~/path` expands relative to the home directory, and `~user` expands to another user's home directory. On Windows, tilde expansion is a no-op.

## Programmatic usage

Use `Factory.defaultListener(Map<String, String>)` to automatically include a report listener when `paramixel.report.file` is present in the configuration:

```java
Map<String, String> config = Configuration.defaultProperties();
// config already contains paramixel.report.file if set via system properties

Runner runner = Runner.builder()
        .listener(Factory.defaultListener(config))
        .build();
Result result = runner.run(action);
```

## Custom report listeners

Add report listeners directly to a custom listener chain:

```java
import org.paramixel.core.internal.listener.JsonReportListener;
import org.paramixel.core.internal.listener.HtmlReportListener;
import org.paramixel.core.internal.listener.XmlReportListener;
import org.paramixel.core.internal.listener.ReportListener;

Listener listener = new CompositeListener(
        new StatusListener(),
        new SummaryListener(new TreeSummaryRenderer()),
        new JsonReportListener("target/paramixel/report.json"),
        new HtmlReportListener("target/paramixel/report.html"));

Runner runner = Runner.builder()
        .listener(new SafeListener(listener))
        .build();
```

You can write multiple report formats simultaneously by adding several report listeners.

## Maven plugin

```xml
<configuration>
    <reportFile>${project.build.directory}/paramixel/paramixel.json</reportFile>
</configuration>
```

CLI:

```bash
./mvnw test -Dparamixel.report.file=target/paramixel/paramixel.html
```

## Gradle plugin

```kotlin
paramixel {
    reportFile.set(layout.buildDirectory.file("paramixel/paramixel.json").map { it.asFile.absolutePath })
}
```

CLI:

```bash
./gradlew paramixelTest -Pparamixel.report.file=build/paramixel/paramixel.html
```
