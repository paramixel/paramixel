---
title: Listener
description: Observe Paramixel execution.
---

# Listener

`Listener` receives execution callbacks.

## Callbacks

```java
default void runStarted(Runner runner)
default void beforeAction(Result result)
default void actionThrowable(Result result, Throwable throwable)
default void afterAction(Result result)
default void skipAction(Result result)
default void runCompleted(Runner runner, Result result)
```

All callbacks receive a `Result` (which wraps `Action`, `Status`, and timing information), not `Context` + `Action` separately. Use `result.getAction()` to access the action, `result.getStatus()` for the status, and `result.getRunDuration()` for timing.

The `skipAction(Result)` callback fires for actions skipped through `Action.skip(...)`, such as descendants skipped because a parent determined they should not run. A `SkipException` thrown from a running `Direct` action produces a `SKIP` result and flows through `afterAction(Result)`.

## Built-in listener factory

```java
Factory.defaultListener()
```

`Factory.defaultListener()` combines `StatusListener` (per-action status lines) with `SummaryListener` using `TreeSummaryRenderer` (tree-style run summary), wrapped in `SafeListener`.

There is no separate `treeListener()` — `Factory.defaultListener()` is the standard entry point.

### Configuration-aware factory

```java
Factory.defaultListener(Map<String, String> configuration)
```

When `paramixel.report.file` is present in the configuration, this overload automatically includes the appropriate report listener (`ReportListener`, `JsonReportListener`, `XmlReportListener`, or `HtmlReportListener`) in the listener chain.

## Report listeners

Paramixel includes four report listeners that write per-run summary files:

| Listener | Format | Description |
|---|---|---|
| `ReportListener` | Text | Plain-text tree summary |
| `JsonReportListener` | JSON | Structured JSON with `name`, `kind`, `status`, `runDuration`, `message`, `exception`, `children` |
| `XmlReportListener` | XML | Element-based XML report |
| `HtmlReportListener` | HTML | Self-contained interactive HTML with tree view, search, and expand/collapse |

All report listeners accept a file path in their constructor:

```java
new JsonReportListener("target/paramixel/report.json")
new HtmlReportListener("target/paramixel/report.html")
```

See [Reporting](reporting.md) for full configuration details.

## Safe listener wrapper

`org.paramixel.core.internal.listener.SafeListener` wraps another listener and catches listener-thrown exceptions so they do not break execution. `Error` subclasses (such as `OutOfMemoryError` and `StackOverflowError`) are rethrown immediately rather than caught and logged.

## Custom listener example

```java
Listener listener = new Listener() {
    @Override
    public void beforeAction(Result result) {
        System.out.println("starting " + result.getAction().getName());
    }
};

Runner runner = Runner.builder().listener(listener).build();
```
