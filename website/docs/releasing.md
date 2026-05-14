---
title: Releasing
description: How to release Paramixel to Maven Central.
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

# Publish the public key to key servers (required for Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver keys.openpgp.org --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver pgp.mit.edu --send-keys XXXXXXXXXXXXXXXX

# Verify signing works
echo "test" | gpg --batch --clearsign >/dev/null
```

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`
- The release version must not already end in `-POST`

## Release process

### Step 1 — Prepare the release branch

From `main`, create the release branch, set the version, validate the build, then push:

```bash
git checkout main
git pull
git checkout -b release/<VERSION>

./mvnw versions:set \
  -DnewVersion=<VERSION> \
  -DprocessAllModules \
  -DgenerateBackupPoms=false

./mvnw spotless:apply
./mvnw clean install
./gradlew clean check --no-daemon
./scripts/build-documentation.sh

git add -A
git commit -s -m "Release <VERSION>"
git push -u origin release/<VERSION>
```

**If any command in the build fails:** delete the release branch and fix the issue on `main`:

```bash
git checkout main
git branch -D release/<VERSION>
```

Fix the issue on `main`, then start over from Step 1.

### Step 2 — Wait for CI to pass

CI runs automatically on the release branch. Wait for all jobs to pass before proceeding.

See: [GitHub Actions](https://github.com/paramixel/paramixel-private/actions)

**If CI fails:** fix the issue on the release branch, push, and wait for CI to pass again. If the issue requires changes on `main`, delete the release branch (local and remote), fix `main`, and start over:

```bash
git push origin --delete release/<VERSION>
git branch -D release/<VERSION>
```

### Step 3 — Deploy to Maven Central

Once CI is green, deploy the release artifacts to Sonatype Central. The deployment remains in a pending state until you explicitly publish it in the next step.

```bash
# Ensure you are on the release branch
git checkout release/<VERSION>

./mvnw -Prelease clean deploy
```

### Step 4 — Verify and publish to Maven Central

> **Maven Central releases are immutable and cannot be undone or overwritten.**

Before proceeding, verify the deployment at [Sonatype Central](https://central.sonatype.com):

- Confirm the version number is correct
- Confirm the artifacts (JAR, sources, javadoc, POM) are present and valid
- Confirm GPG signatures are present

Once verified, publish the deployment:

```bash
./mvnw -Prelease central-publishing:publish
```

### Step 5 — Tag the release

After a successful publish, create and push the tag:

```bash
git tag -a v<VERSION> -m "Release <VERSION>"
git push origin v<VERSION>
```

### Step 6 — Bump main to the next development version

```bash
git checkout main
git pull

./mvnw versions:set \
  -DnewVersion=<VERSION>-POST \
  -DprocessAllModules \
  -DgenerateBackupPoms=false

./mvnw spotless:apply
./mvnw clean install
./gradlew clean check --no-daemon

git add -A
git commit -s -m "Prepare for development"
git push
```

## Troubleshooting

- **GPG signing failed** — Verify your key is valid and unexpired with `gpg --list-secret-keys`. Test with `echo "test" | gpg --batch --clearsign`.
- **Maven settings not found** — Create `~/.m2/settings.xml` with Sonatype Central credentials.
- **"main is not synced with origin/main"** — Pull or push to sync: `git pull` or `git push`. Check for diverged branches.
- **"Local tag already exists"** — Check with `git tag -l`; delete if stale with `git tag -d vX.Y.Z`.
- **"Remote tag already exists"** — Delete remote tag: `git push origin :refs/tags/vX.Y.Z`. Only do this if the previous release was incomplete or failed.
- **Central deployment fails** — Verify credentials at https://central.sonatype.com and ensure your GPG public key is published to key servers.

### Error recovery

- **CI fails on the release branch:** Fix on the branch or delete it and start over. No tag or deploy has happened yet.
- **Maven Central deploy fails:** Do not push the tag. Fix the issue and retry the deploy.
- **Deploy succeeded but publish not yet done:** Retry `./mvnw -Prelease central-publishing:publish`, or drop the deployment with `./mvnw -Prelease central-publishing:drop` to start over.
- **Publish failed after partial completion:** Check the deployment status at https://central.sonatype.com. If it's still in a publishable state, retry `./mvnw -Prelease central-publishing:publish`.
- **Tag pushed but deploy failed:** Delete the remote tag (`git push origin :refs/tags/vX.Y.Z`), fix the issue, redeploy, then re-tag.
- **Post-release bump failed on main:** Manually set the version, commit, and push.
