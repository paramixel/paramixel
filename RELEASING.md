# Releasing Paramixel

This repository publishes artifacts to Maven Central using the `release.sh` script.

## Prerequisites

### Java Installations

The release script verifies builds against three Java versions. Set the following environment variables:

```bash
export JAVA_17_HOME=/path/to/jdk17
export JAVA_21_HOME=/path/to/jdk21
export JAVA_25_HOME=/path/to/jdk25
```

Each must point to a JDK installation with a working `bin/java` executable.

### Maven Central Credentials

Deploy mode requires a Maven `settings.xml` file at `~/.m2/settings.xml` with your Sonatype Central credentials:

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

### GPG Signing Key

Deploy mode requires a local GPG signing key. Maven Central requires that all published artifacts are signed and that the signing key is published to public key servers.

```bash
# Generate a dedicated GPG key for releases (if you don't have one)
gpg --full-generate-key
# RSA 4096, 2-year expiry recommended

# List your key to get the Key ID
gpg --list-secret-keys --keyid-format=long
# Look for: sec   rsa4096/XXXXXXXXXXXXXXXX

# Publish the public key to key servers (required for Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver keys.openpgp.org --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver pgp.mit.edu --send-keys XXXXXXXXXXXXXXXX

# Verify signing works
echo "test" | gpg --batch --clearsign >/dev/null
```

### Repository State

- You must be on the `main` branch
- The working tree must be clean (no uncommitted changes)
- `main` must be synced with the remote (no ahead/behind commits)
- No existing release branch or tag for the target version

## Usage

```bash
./release.sh --version <VERSION> [options]
```

### Options

| Option | Description |
|---|---|
| `--version <version>` | Release version, e.g. `2.1.0` or `1.0.0-alpha` |
| `--next-version <version>` | Next development version. Defaults to `<version>-POST` |
| `--gradle-plugin-dir <dir>` | Gradle plugin directory. Defaults to `gradle-plugin` |
| `--dry-run` | Validate only; no deploy, commits, tags, or pushes. **Default** |
| `--deploy` | Publish release artifacts and push git refs |
| `--yes`, `-y` | Skip deploy confirmation prompt |
| `--help`, `-h` | Show help |

### Dry Run (Default)

Validate everything without making any changes. The script will run all builds, create a temporary release branch, and then clean up automatically:

```bash
./release.sh --version 2.1.0
```

If the dry run fails, tracked changes are reset and the temporary release branch is deleted.

### Deploy

Publish artifacts to Maven Central, create the release branch and tag, and update `main`:

```bash
./release.sh --version 2.1.0 --deploy
```

You will be prompted to type the version number to confirm. Use `-y` to skip confirmation:

```bash
./release.sh --version 2.1.0 --deploy -y
```

### Custom Next Version

By default, the post-release development version is `<VERSION>-POST`. Override with `--next-version`:

```bash
./release.sh --version 2.1.0 --deploy --next-version 2.2.0-SNAPSHOT
```

## What Happens During a Release

### 1. Validation

- Checks required environment variables (`JAVA_17_HOME`, `JAVA_21_HOME`, `JAVA_25_HOME`)
- Verifies the working tree is clean and the current branch is `main`
- Fetches from remote and confirms `main` is synced (no ahead/behind)
- Confirms no existing local or remote release branch or tag
- In deploy mode: validates `~/.m2/settings.xml` exists and GPG signing works

### 2. Pre-release Build Verification

Runs the full Maven build on all three Java versions to ensure compatibility:

```bash
JAVA_HOME=$JAVA_25_HOME ./mvnw -B clean verify -Dspotbugs.skip=true -Dpmd.skip=true
JAVA_HOME=$JAVA_21_HOME ./mvnw -B clean verify
JAVA_HOME=$JAVA_17_HOME ./mvnw -B clean verify
```

### 3. Release Branch Creation

- Creates branch `release/<VERSION>`
- Sets project version to `<VERSION>` via `mvn versions:set`

### 4. Release Branch Verification

- Re-runs the full Maven build on all three Java versions
- Installs signed release artifacts locally with Java 17 (`-Prelease clean install`)
- Builds the Gradle plugin with the release version

### 5. Deploy (deploy mode only)

- Commits release changes with sign-off: `Release <VERSION>`
- Creates annotated tag `v<VERSION>`
- Pre-flight checks push permissions for the release branch and tag
- Deploys signed artifacts to Maven Central with Java 17 (`-Prelease clean deploy`)
- Pushes release branch and tag to remote

### 6. Post-release (deploy mode only)

- Switches back to `main`
- Sets next development version (`<VERSION>-POST` or custom `--next-version`)
- Verifies the next development build with Java 17
- Syncs the Gradle plugin version to the next development version
- Commits with sign-off: `Prepare for development`
- Pushes `main` to remote

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`
- The release version must not already end in `-POST`

## Troubleshooting

### Common Issues

1. **"GPG signing failed"**
   - Verify your GPG key is valid: `gpg --list-secret-keys`
   - Check the key hasn't expired
   - Ensure the passphrase is correct (GPG agent may be caching it)
   - Test manually: `echo "test" | gpg --batch --clearsign`

2. **"Maven settings not found: ~/.m2/settings.xml"**
   - Create or update `~/.m2/settings.xml` with your Sonatype Central credentials
   - Ensure the `<server>` `<id>` matches the `distributionManagement` repository ID in the POM

3. **"main is not synced with origin/main"**
   - Pull or push to sync: `git pull` or `git push`
   - Check for diverged branches

4. **"Local tag already exists: vX.Y.Z"**
   - Check existing tags: `git tag -l`
   - Delete if stale: `git tag -d vX.Y.Z`

5. **"Remote tag already exists: vX.Y.Z"**
   - Delete remote tag: `git push origin :refs/tags/vX.Y.Z`
   - Only do this if the previous release was incomplete or failed

6. **Central deployment fails**
   - Verify your Sonatype credentials at https://central.sonatype.com
   - Ensure the GPG public key is published to key servers
   - Check that your account has publishing permissions

### Error Recovery

In **dry-run** mode, the script automatically cleans up on failure by resetting tracked changes, restoring the original branch, and deleting the temporary release branch.

In **deploy** mode, the script leaves local state intact for inspection and recovery if a failure occurs after commits are made. You may need to manually:

```bash
# Undo the last commit (if not yet pushed)
git reset --hard HEAD~1

# Delete a local tag
git tag -d vX.Y.Z

# Delete a local release branch
git branch -D release/X.Y.Z
```

---

Copyright 2026-present Douglas Hoard
