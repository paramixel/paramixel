---
title: Releasing
description: How to release Paramixel to Maven Central and the Gradle Plugin Portal.
---

# Releasing

Paramixel is released through a manual process with CI validation. You create the release branch, push it, wait for CI to pass, then deploy and tag.

## Prerequisites

### Maven Central credentials

A Maven `settings.xml` at `~/.m2/settings.xml` with Sonatype Central credentials:

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

Maven Central requires signed artifacts and that the signing key is published to public key servers.

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

### Gradle Plugin Portal credentials

Publishing the Gradle plugin requires credentials for the Gradle Plugin Portal:

```bash
export GRADLE_PUBLISH_KEY=YOUR_KEY
export GRADLE_PUBLISH_SECRET=YOUR_SECRET
```

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`

## Release process

### Step 1 — Prepare the release branch

From `main`, create the release branch and set the version:

```bash
git checkout main
git pull

git checkout -b release/2.1.0

./mvnw versions:set \
  -DnewVersion=2.1.0 \
  -DprocessAllModules \
  -DgenerateBackupPoms=false

./mvnw spotless:apply

git add -A
git commit -s -m "Release 2.1.0"

git push -u origin release/2.1.0
```

### Step 2 — Wait for CI to pass

CI runs automatically on the release branch. Wait for all jobs to pass before proceeding.

**If CI fails:** fix the issue on the release branch, push, and wait for CI to pass again. If the issue requires changes on `main`, delete the release branch, fix `main`, and start over.

### Step 3 — Deploy to Maven Central

Once CI is green, deploy the release artifacts:

```bash
# Make sure you are on the release branch
git checkout release/2.1.0

./mvnw -Prelease clean deploy
```

### Step 4 — Tag the release

After a successful deploy, create and push the tag:

```bash
git tag -a v2.1.0 -m "Release 2.1.0"
git push origin v2.1.0
```

### Step 5 — Bump main to the next development version

```bash
git checkout main

./mvnw versions:set \
  -DnewVersion=2.1.0-POST \
  -DprocessAllModules \
  -DgenerateBackupPoms=false

./mvnw spotless:apply

# Sync the Gradle plugin version
./build.sh sync-gradle-version

git add -A
git commit -s -m "Prepare for development"

git push
```

### Step 6 — Publish the Gradle plugin

Publish the Gradle plugin to the Gradle Plugin Portal:

```bash
JAVA_17_HOME=/path/to/jdk17 ./build.sh gradle-plugin

cd gradle-plugin
GRADLE_PUBLISH_KEY=YOUR_KEY \
GRADLE_PUBLISH_SECRET=YOUR_SECRET \
JAVA_HOME="$JAVA_17_HOME" \
  ./gradlew publishPlugins --no-daemon
cd ..
```

## Troubleshooting

- **GPG signing failed** — Verify your key is valid and unexpired with `gpg --list-secret-keys`. Test with `echo "test" | gpg --batch --clearsign`.
- **Maven settings not found** — Create `~/.m2/settings.xml` with Sonatype Central credentials.
- **Tag already exists** — Check with `git tag -l`; delete if stale with `git tag -d vX.Y.Z`.
- **Central deployment fails** — Verify credentials at https://central.sonatype.com and ensure your GPG public key is published to key servers.

### Error recovery

- **CI fails on the release branch:** Fix on the branch or delete it and start over. No tag or deploy has happened yet.
- **Maven Central deploy fails:** Do not push the tag. Fix the issue and retry the deploy.
- **Tag pushed but deploy failed:** Delete the remote tag (`git push origin :refs/tags/vX.Y.Z`), fix the issue, redeploy, then re-tag.
- **Post-release bump failed on main:** Manually set the version, commit, and push.
