# Releasing Paramixel

This repository publishes artifacts to Maven Central using `./release.sh` and the Maven `release` profile.

## Prerequisites

- Run the script from the repository root where `mvnw` is available.
- Use a clean git working tree.
- Start from the `main` branch.
- Have push access to the remote tracked by `main` (or `origin` if no branch remote is configured).
- Have `gpg` available locally because the Maven `release` profile signs artifacts during `verify`.
- Have `~/.m2/settings.xml` configured with a `<server><id>central</id>` entry for Sonatype Central publishing.
- Have Maven settings configured for publishing to Maven Central.

## Release

Run the release script with the target version:

```bash
./release.sh 0.1.0-alpha-2
```

The script performs the full release flow:

1. Validates the version format.
2. Verifies the repository is on a clean `main` branch.
3. Verifies the release branch and tag do not already exist locally or on the remote.
4. Runs a baseline release build with `./mvnw -Prelease clean verify`.
5. Creates the release branch `release/<VERSION>`.
6. Sets the project version to `<VERSION>`.
7. Runs `./mvnw -Prelease clean verify`.
8. Runs `./mvnw -Prelease clean deploy`.
9. Commits the release changes as `Release <VERSION>`.
10. Creates the annotated tag `v<VERSION>`.
11. Pushes the release branch.
12. Switches back to `main`.
13. Sets the project version to `<VERSION>-POST`.
14. Runs `./mvnw -Prelease clean verify`.
15. Commits the post-release changes as `Prepare for development`.
16. Pushes `main`.
17. Pushes the release tag.

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`
- The release version must not already end in `-POST`

---

Copyright 2026-present Douglas Hoard
