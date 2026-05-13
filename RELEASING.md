# Releasing Paramixel

This repository publishes artifacts to Maven Central and the Gradle Plugin Portal through a manual release process with CI validation.

The release process is automated by `scripts/release.sh`. Run `./scripts/release.sh --help` for usage.

## Prerequisites

### Maven Central Credentials

A Maven `settings.xml` file at `~/.m2/settings.xml` with your Sonatype Central credentials:

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

Maven Central requires all published artifacts to be signed and the signing key published to public key servers.

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

### Gradle Plugin Portal Credentials

Publishing the Gradle plugin requires credentials for the Gradle Plugin Portal. Set the following environment variables or Gradle properties:

```bash
export GRADLE_PUBLISH_KEY=YOUR_KEY
export GRADLE_PUBLISH_SECRET=YOUR_SECRET
```

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`
- The release version must not already end in `-POST`

## Release Process

### Phase 1 — Prepare the release branch

```bash
JAVA_17_HOME=/path/to/jdk17 ./scripts/release.sh phase1 <VERSION>
```

Checks out `main`, pulls latest, creates the `release/<VERSION>` branch, sets the version across all modules, applies spotless formatting, commits, runs a full build (`./scripts/build.sh`) and documentation build (`./scripts/build-documentation.sh`), and pushes the branch only if both succeed. Prints CI wait instructions on completion.

**Prerequisites:** `JAVA_17_HOME` must point to a JDK 17 installation. `node`, `npm`, `jq`, and `awk` must be on `PATH` for the documentation build.

**If CI fails:** fix the issue on the release branch, push, and wait for CI to pass again. If the issue requires changes on `main`, delete the release branch, fix `main`, and start over.

### Phase 2 — Deploy to Maven Central

```bash
./scripts/release.sh phase2
```

Runs `./mvnw -Prelease clean deploy` on the current release branch. This uploads the artifact bundle to Sonatype Central. Sonatype validates the bundle on upload, but does **not** publish it yet. The deployment remains in a pending state until you explicitly publish it in the next phase.

**Prerequisites:** You must already be on the `release/<VERSION>` branch (left there after phase 1).

### Phase 3 — Verify and publish to Maven Central

```bash
./scripts/release.sh phase3
```

> **Maven Central releases are immutable and cannot be undone or overwritten.** Before proceeding, verify the deployment at [Sonatype Central](https://central.sonatype.com):
>
> - Confirm the version number is correct
> - Confirm the artifacts (JAR, sources, javadoc, POM) are present and valid
> - Confirm GPG signatures are present

Prompts for confirmation, then runs `./mvnw -Prelease central-publishing:publish` to publish the deployment.

### Phase 4 — Tag the release

```bash
./scripts/release.sh phase4 <VERSION>
```

Creates an annotated tag `v<VERSION>` and pushes it to origin.

### Phase 5 — Bump main to the next development version

```bash
./scripts/release.sh phase5 <VERSION>
```

Checks out `main`, pulls latest, sets the version to `<VERSION>-POST` across all modules, applies spotless formatting, runs `./mvnw clean install`, commits, and pushes.

### Phase 6 — Publish the Gradle plugin

This phase is not yet automated. Publish the Gradle plugin to the Gradle Plugin Portal manually:

```bash
JAVA_17_HOME=/path/to/jdk17 ./scripts/build.sh gradle-plugin

cd gradle-plugin
GRADLE_PUBLISH_KEY=YOUR_KEY \
GRADLE_PUBLISH_SECRET=YOUR_SECRET \
JAVA_HOME="$JAVA_17_HOME" \
  ./gradlew publishPlugins --no-daemon
cd ..
```

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

- **CI fails on the release branch:** Fix on the branch or delete it and start over. No tag or deploy has happened yet.
- **Maven Central deploy fails:** Do not push the tag. Fix the issue and retry the deploy.
- **Deploy succeeded but publish not yet done:** Retry `./mvnw -Prelease central-publishing:publish`, or drop the deployment with `./mvnw -Prelease central-publishing:drop` to start over.
- **Publish failed after partial completion:** Check the deployment status at https://central.sonatype.com. If it's still in a publishable state, retry `./mvnw -Prelease central-publishing:publish`.
- **Tag pushed but deploy failed:** Delete the remote tag (`git push origin :refs/tags/vX.Y.Z`), fix the issue, redeploy, then re-tag.
- **Post-release bump failed on main:** Manually set the version, commit, and push.

---

Copyright 2026-present Douglas Hoard
