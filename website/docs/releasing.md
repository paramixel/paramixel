---
title: Releasing
description: How to release Paramixel to Maven Central.
---

# Releasing

Paramixel is released through a manual process with CI validation. The release process is automated by `scripts/release.sh`.

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

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`

## Release process

### Phase 1 — Prepare the release branch

```bash
./scripts/release.sh phase1 <VERSION>
```

Checks out `main`, pulls latest, creates the `release/<VERSION>` branch, sets the version across all modules, applies spotless formatting, commits, and pushes the branch. Prints CI wait instructions on completion.

**If CI fails:** fix the issue on the release branch, push, and wait for CI to pass again. If the issue requires changes on `main`, delete the release branch, fix `main`, and start over.

### Phase 2 — Deploy to Maven Central

```bash
./scripts/release.sh phase2 <VERSION>
```

Checks out the release branch and deploys to Sonatype Central. The deployment remains in a pending state until you explicitly publish it in the next phase.

### Phase 3 — Verify and publish to Maven Central

```bash
./scripts/release.sh phase3
```

> **Maven Central releases are immutable and cannot be undone or overwritten.** Before proceeding, verify the deployment at [Sonatype Central](https://central.sonatype.com):
>
> - Confirm the version number is correct
> - Confirm the artifacts (JAR, sources, javadoc, POM) are present and valid
> - Confirm GPG signatures are present

Prompts for confirmation, then publishes the deployment.

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

## Troubleshooting

- **GPG signing failed** — Verify your key is valid and unexpired with `gpg --list-secret-keys`. Test with `echo "test" | gpg --batch --clearsign`.
- **Maven settings not found** — Create `~/.m2/settings.xml` with Sonatype Central credentials.
- **Tag already exists** — Check with `git tag -l`; delete if stale with `git tag -d vX.Y.Z`.
- **Central deployment fails** — Verify credentials at https://central.sonatype.com and ensure your GPG public key is published to key servers.

### Error recovery

- **CI fails on the release branch:** Fix on the branch or delete it and start over. No tag or deploy has happened yet.
- **Maven Central deploy fails:** Do not push the tag. Fix the issue and retry the deploy.
- **Deploy succeeded but publish not yet done:** Retry `./mvnw -Prelease central-publishing:publish`, or drop the deployment with `./mvnw -Prelease central-publishing:drop` to start over.
- **Tag pushed but deploy failed:** Delete the remote tag (`git push origin :refs/tags/vX.Y.Z`), fix the issue, redeploy, then re-tag.
- **Post-release bump failed on main:** Manually set the version, commit, and push.
