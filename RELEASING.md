# Releasing Paramixel

This repository publishes artifacts to Maven Central through a manual release process with CI validation.

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

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`
- The release version must not already end in `-POST`

## Release Process

### Step 1 — Prepare the release branch

From `main`, create the release branch, set the version, validate the build, then push:

```bash
git checkout main
git pull
git checkout -b release/<VERSION>

./mvnw versions:set-property -Dproperty=revision -DnewVersion=<VERSION> -DgenerateBackupPoms=false

./mvnw spotless:apply
./mvnw clean install
./gradlew clean check --no-daemon
./scripts/build-documentation.sh

git add -A
git commit -s -m "release: Release <VERSION>"
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

See: [GitHub Actions](https://github.com/paramixel/paramixel/actions)

**If CI fails:** fix the issue on the release branch, push, and wait for CI to pass again. If the issue requires changes on `main`, delete the release branch (local and remote), fix `main`, and start over:

```bash
git push origin --delete release/<VERSION>
git branch -D release/<VERSION>
```

### Step 3 — Deploy to Maven Central

Once CI is green, deploy the release artifacts to Sonatype Central. The deployment remains pending until you explicitly publish it from the Maven Central Publishing Portal.

```bash
# Ensure you are on the release branch
git checkout release/<VERSION>

./mvnw -Prelease clean deploy
```

### Step 4 — Verify and publish to Maven Central

> **Maven Central releases are immutable and cannot be undone or overwritten.**

Before proceeding, verify the pending deployment at [Sonatype Central](https://central.sonatype.com):

- Confirm the version number is correct
- Confirm the expected publishable artifacts are present
- Confirm each publishable artifact has the required JAR, sources, javadoc, and POM files
- Confirm GPG signatures are present
- Confirm Central validation completed successfully

Once verified, publish the deployment from the Maven Central Publishing Portal.

### Step 5 — Tag the release

After a successful publish, create and push the tag:

```bash
git tag -a v<VERSION> -m "Release <VERSION>"
git push origin v<VERSION>
```

### Step 6 — Publish documentation

After tagging, publish the documentation site. This builds the Docusaurus site and deploys it via rsync over SSH.

Requires the `WWW_PARAMIXEL_ORG` environment variable, or pass `--ssh-host`.

```bash
./scripts/publish-documentation.sh
```

To preview what will be deployed without transferring files:

```bash
./scripts/publish-documentation.sh --dry-run
```

If the documentation was already built during Step 1, skip the build step:

```bash
./scripts/publish-documentation.sh --skip-build
```

### Step 7 — Bump main to the next development version

```bash
git checkout main
git pull

./mvnw versions:set-property -Dproperty=revision -DnewVersion=<VERSION>-POST -DgenerateBackupPoms=false

./mvnw spotless:apply
./mvnw clean install
./gradlew clean check --no-daemon

git add -A
git commit -s -m "chore: Prepare for development"
git push
```

## Troubleshooting

### Common Issues

1. **"GPG signing failed"**
   - Verify your GPG key is valid: `gpg --list-secret-keys`
   - Check the key has not expired
   - Ensure the passphrase is correct; GPG agent may be caching it
   - Test manually: `echo "test" | gpg --batch --clearsign`

2. **"Maven settings not found: ~/.m2/settings.xml"**
   - Create or update `~/.m2/settings.xml` with your Sonatype Central credentials
   - Ensure the `<server>` `<id>` matches the Central publishing server ID configured in the POM

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
   - Review Maven output for missing sources, javadoc, signatures, or POM metadata

### Error Recovery

- **CI fails on the release branch:** Fix on the branch or delete it and start over. No tag or deploy has happened yet.
- **Maven Central deploy fails:** Do not push the tag. Fix the issue and rerun `./mvnw -Prelease clean deploy`.
- **Deploy succeeded but publish not yet done:** Review the pending deployment in the Maven Central Publishing Portal. If valid, publish it from the portal. If it should not be published, drop the deployment from the portal, fix the issue locally, and rerun `./mvnw -Prelease clean deploy`.
- **Publish failed after validation:** Check the deployment status and validation details in the Maven Central Publishing Portal. If it can be retried from the portal, retry there. Otherwise drop the deployment from the portal, fix the issue locally, and rerun `./mvnw -Prelease clean deploy`.
- **Tag pushed but deploy failed:** Delete the remote tag (`git push origin :refs/tags/vX.Y.Z`), fix the issue, redeploy, then re-tag.
- **Post-release bump failed on main:** Manually set the version, commit, and push.

---

Copyright 2026-present Douglas Hoard
