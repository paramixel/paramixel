---
title: Troubleshooting
description: Common discovery, configuration, and execution issues.
---

# Troubleshooting

## No factories discovered

Check that factory methods are public static, annotated with `@Paramixel.Factory`, and return `Action`, `Builder`, or `null`. If you expect discovery to fail the build when empty, set:

```properties
paramixel.failIfNoTests=true
```

## Factory is not selected

Check filters:

```bash
-Dparamixel.match.package.regex=...
-Dparamixel.match.class.regex=...
-Dparamixel.match.tag.regex=...
```

Regex filters use `find()` semantics, not full-string-only matching.

## Factory is skipped

A factory returning `null` creates a skipped action outcome. A method annotated with `@Paramixel.Disabled` is excluded from discovery entirely.

## Report file missing

Set `paramixel.report.file` and ensure the process can write the parent directory.

## Unexpected exit code

`paramixel.failureOnAbort` defaults to `true`; aborted results produce failing exit codes by default. Set `paramixel.failureOnSkip=true` if skipped results should also fail CI. Use `paramixel.failFast=true` to stop scheduling remaining tests after the first failed or aborted action.
