---
title: Reporting
description: Console and file reporting from the default listener chain.
---

# Reporting

`Listener.defaultListener(Configuration)` creates the default listener chain. Console output honors `paramixel.ansi` and listener exclusions. File reports are enabled with `paramixel.report.file`.

```properties
paramixel.report.file=target/paramixel-report.json
paramixel.ansi=auto
paramixel.listener.exclude=quiet
```

## File formats

The report format is selected from the file extension:

- `.json` for JSON
- `.xml` for XML
- `.html` for HTML
- `.log` or `.txt` for plain text
- unknown or missing extensions also use plain text while preserving the supplied filename

## Listener exclusions

`paramixel.listener.exclude` accepts comma-separated tokens: `status.header`, `status.footer`, `summary.header`, `summary.tree`, `summary.footer`, plus shorthands `status`, `quiet`, and `all`.
