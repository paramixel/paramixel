---
title: CI/CD
description: Run Paramixel reliably in continuous integration.
---

# CI/CD

Use system properties to make CI runs explicit and reproducible.

```bash
mvn test \
  -Dparamixel.parallelism=4 \
  -Dparamixel.failIfNoTests=true \
  -Dparamixel.failureOnAbort=true \
  -Dparamixel.report.file=target/paramixel-report.json
```

## Recommendations

- Set `paramixel.failIfNoTests=true` so discovery regressions fail the build.
- Generate a report file in `target/` or the CI artifact directory.
- Use `paramixel.match.tag.regex` for smoke or critical subsets.
- Keep parallel tests isolated; do not share mutable fixtures across parallel children.
- In Maven builds, use `-Dparamixel.skipTests=true` only when intentionally skipping Paramixel execution.
