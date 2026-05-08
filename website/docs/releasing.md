---
title: Releasing
description: How to release Paramixel to Maven Central and the Gradle Plugin Portal.
---

# Releasing

Paramixel is released using the `release.sh` script, which validates, builds, deploys, and tags a release in a single command.

## Prerequisites

### Java installations

The release script verifies builds against three Java versions. Set the following environment variables:

```bash
export JAVA_17_HOME=/path/to/jdk17
export JAVA_21_HOME=/path/to/jdk21
export JAVA_25_HOME=/path/to/jdk25
```

### Maven Central credentials

Deploy mode requires a Maven `settings.xml` at `~/.m2/settings.xml` with Sonatype Central credentials:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN</password>
    </server>
  </servers>
</settings>
```

### GPG signing key

Deploy mode requires a local GPG signing key. Maven Central requires signed artifacts and that the signing key is published to public key servers.

```bash
# Generate a GPG key (if you don't have one)
gpg --full-generate-key
# RSA 4096, 2-year expiry recommended

# Get your Key ID
gpg --list-secret-keys --keyid-format=long

# Publish to key servers
gpg --keyserver keyserver.ubuntu.com --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver keys.openpgp.org --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver pgp.mit.edu --send-keys XXXXXXXXXXXXXXXX

# Verify signing works
echo "test" | gpg --batch --clearsign >/dev/null
```

## Quick start

```bash
# Dry run (default) - validate only, no changes made
./release.sh --version 2.1.0

# Deploy - publish to Maven Central and push release refs
./release.sh --version 2.1.0 --deploy
```

You will be prompted to type the version number to confirm the deploy. Use `-y` to skip confirmation:

```bash
./release.sh --version 2.1.0 --deploy -y
```

## Options

| Option | Description |
|---|---|
| `--version <version>` | Release version, e.g. `2.1.0` or `1.0.0-alpha` |
| `--next-version <version>` | Next development version. Defaults to `<version>-POST` |
| `--gradle-plugin-dir <dir>` | Gradle plugin directory. Defaults to `gradle-plugin` |
| `--dry-run` | Validate only; no deploy, commits, tags, or pushes. **Default** |
| `--deploy` | Publish release artifacts and push git refs |
| `--yes`, `-y` | Skip deploy confirmation prompt |
| `--help`, `-h` | Show help |

## What happens during a release

### 1. Validation

- Checks required environment variables (`JAVA_17_HOME`, `JAVA_21_HOME`, `JAVA_25_HOME`)
- Verifies the working tree is clean and the current branch is `main`
- Confirms `main` is synced with the remote
- Confirms no existing release branch or tag for the target version
- In deploy mode: validates `~/.m2/settings.xml` and GPG signing

### 2. Pre-release build verification

Runs the full Maven build on all three Java versions:

```bash
JAVA_HOME=$JAVA_25_HOME ./mvnw -B clean verify -Dspotbugs.skip=true -Dpmd.skip=true
JAVA_HOME=$JAVA_21_HOME ./mvnw -B clean verify
JAVA_HOME=$JAVA_17_HOME ./mvnw -B clean verify
```

### 3. Release branch creation

- Creates branch `release/<VERSION>`
- Sets project version to `<VERSION>` via `mvn versions:set`

### 4. Release branch verification

- Re-runs the full Maven build on all three Java versions
- Installs signed release artifacts locally (`-Prelease clean install`)
- Builds the Gradle plugin with the release version

### 5. Deploy (deploy mode only)

- Commits release changes: `Release <VERSION>`
- Creates annotated tag `v<VERSION>`
- Pre-flight checks push permissions
- Deploys signed artifacts to Maven Central
- Pushes release branch and tag to remote

### 6. Post-release (deploy mode only)

- Switches back to `main`
- Sets next development version (`<VERSION>-POST` or custom `--next-version`)
- Verifies the next development build
- Syncs the Gradle plugin version
- Commits: `Prepare for development`
- Pushes `main` to remote

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`

## Troubleshooting

- **GPG signing failed** — Verify your key is valid and unexpired with `gpg --list-secret-keys`. Test with `echo "test" | gpg --batch --clearsign`.
- **Maven settings not found** — Create `~/.m2/settings.xml` with Sonatype Central credentials.
- **main not synced** — Run `git pull` or `git push` to sync with the remote.
- **Tag already exists** — Check with `git tag -l`; delete if stale with `git tag -d vX.Y.Z`.
- **Central deployment fails** — Verify credentials at https://central.sonatype.com and ensure your GPG public key is published to key servers.

In dry-run mode, the script automatically cleans up on failure. In deploy mode, local state is left intact for manual recovery if a failure occurs after commits are made.
