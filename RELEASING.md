# Releasing Paramixel

This repository publishes artifacts to Maven Central using GitHub Actions and the Maven `release` profile.

## Prerequisites

### GitHub Secrets Configuration

The following secrets must be configured in your repository settings (Settings → Secrets and variables → Actions):

1. **GPG_PRIVATE_KEY** - The GPG private key for signing artifacts
2. **GPG_PASSPHRASE** - The passphrase for your GPG private key
3. **CENTRAL_USERNAME** - Your Sonatype Central username
4. **CENTRAL_TOKEN** - Your Sonatype Central token

### Creating a Dedicated GPG Key for Releases

It's recommended to use a dedicated GPG key for automated releases:

```bash
# 1. Generate a new GPG key specifically for Paramixel releases
gpg --full-generate-key

# Select the following options:
# - Type: (1) RSA and RSA (default)
# - Key size: 4096 bits
# - Expiry: 2 years (recommended for automation keys)
# - Real name: Paramixel Release Bot
# - Email: your-email@example.com
# - Comment: Automated releases for Paramixel project

# 2. List the key to get the Key ID
gpg --list-secret-keys --keyid-format=long

# Look for output like:
# sec   rsa4096/XXXXXXXXXXXXXXXX 2024-01-01 [SC] [expires: 2026-01-01]
# Note the XXXXXXXXXXXXXXXX part - this is your Key ID

# 3. Export the private key
gpg --export-secret-keys -a XXXXXXXXXXXXXXXX > paramixel-release-private.asc

# 4. Export the public key (for publishing to key servers)
gpg --export -a XXXXXXXXXXXXXXXX > paramixel-release-public.asc

# 5. Publish the public key to key servers (required for Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver keys.openpgp.org --send-keys XXXXXXXXXXXXXXXX
gpg --keyserver pgp.mit.edu --send-keys XXXXXXXXXXXXXXXX

# 6. Copy the ENTIRE contents of paramixel-release-private.asc 
#    (including -----BEGIN/END----- lines) to the GPG_PRIVATE_KEY secret

# 7. IMPORTANT: Securely delete the key files
shred -vfz paramixel-release-*.asc
# Or on systems without shred:
# rm -P paramixel-release-*.asc  # macOS
# rm paramixel-release-*.asc     # Windows/other (less secure)
```

### Repository Requirements

- Releases must be triggered from the `main` branch
- You must have write permissions to the repository
- The repository uses self-hosted runners (group: Default, labels: runner)
- Environment protection rules are configured for the `release` environment

### Setting up Environment Protection

1. Go to Settings → Environments
2. Create a new environment called `release`
3. Configure protection rules:
   - Required reviewers: 1-2 maintainers
   - Restrict to `main` branch only
   - Optional: Add the same secrets at the environment level for extra security

## Release Process

### Triggering a Release

1. Go to the **Actions** tab in your GitHub repository
2. Select the **Release** workflow from the left sidebar
3. Click **Run workflow** button
4. Enter the release version (e.g., `1.0.0` or `1.0.0-alpha-2`)
5. Click the green **Run workflow** button
6. If environment protection is enabled, approve the release when prompted

### Automated Release Steps

The workflow performs the following steps automatically:

1. **Validation Phase**
   - Validates all required secrets are configured
   - Validates version format (MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-label)
   - Verifies no existing release branch or tag exists
   - Confirms execution from `main` branch
   - Performs security checks on the runner environment

2. **Build and Test Phase**
   - Runs full test suite on Java 17, 21, and 25
   - Executes SpotBugs and PMD analysis (except Java 25)
   - Verifies all tests pass before proceeding

3. **Release Execution Phase**
   - Creates release branch `release/<VERSION>`
   - Sets project version to `<VERSION>`
   - Builds and signs artifacts with GPG
   - Deploys to Maven Central with automatic publishing
   - Commits release changes with sign-off
   - Creates annotated tag `v<VERSION>`
   - Pushes release branch

4. **Post-Release Phase**
   - Switches back to `main` branch
   - Sets version to `<VERSION>-POST`
   - Commits post-release changes
   - Pushes updated `main` branch
   - Pushes release tag

5. **GitHub Release Creation**
   - Creates a minimal GitHub Release with Maven coordinates
   - Can be edited later to add detailed release notes

6. **SLSA Provenance Generation**
   - Generates supply chain security attestations
   - Creates cryptographically signed provenance

## Security Features

The release workflow implements enhanced security measures:

- **Secure Secret Handling**: Secrets are never written to disk on self-hosted runners
- **RAM Disk Usage**: Temporary files stored in RAM (`/dev/shm`) when available
- **Comprehensive Cleanup**: All sensitive data is securely removed after use
- **Audit Logging**: Release attempts are logged for security monitoring
- **Environment Protection**: Requires approval for production releases
- **SLSA Provenance**: Supply chain security attestations

## Monitoring the Release

1. Watch the workflow progress in the Actions tab
2. Each job shows detailed logs for troubleshooting
3. The workflow will fail fast on any errors
4. Maven Central artifacts appear within 30 minutes of successful deployment

## Conventions

- Release tag: `v<VERSION>`
- Release branch: `release/<VERSION>`
- Post-release development version: `<VERSION>-POST`
- Accepted version format: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`
- The release version must not already end in `-POST`

## Troubleshooting

### Common Issues

1. **Secret validation fails**
   - Ensure all required secrets are properly configured
   - Check that secrets don't contain extra whitespace

2. **Version already exists**
   - Check for existing branches/tags with: `git tag -l` and `git branch -a`
   - Remove old tags if needed: `git push origin :refs/tags/vX.Y.Z`

3. **GPG signing fails**
   - Verify your GPG key is valid: `gpg --list-secret-keys`
   - Check the key hasn't expired
   - Ensure the passphrase is correct

4. **Central deployment fails**
   - Verify your Sonatype credentials at https://central.sonatype.com
   - Check that your account has publishing permissions
   - Ensure the GPG public key is published to key servers

5. **Self-hosted runner issues**
   - Check that `/dev/shm` is available for RAM disk operations
   - Verify runner has sufficient permissions
   - Ensure runner environment is clean

### Manual Cleanup (if needed)

If a release fails and leaves the environment in an inconsistent state:

```bash
# On the self-hosted runner:
# Kill GPG agents
gpgconf --kill all

# Clear any remaining sensitive files
find /tmp /dev/shm -name "*gpg*" -o -name "*settings*.xml" | xargs -r shred -vfz

# Clear Maven cache if needed
rm -rf ~/.m2/repository/org/paramixel/*-SNAPSHOT
```

### Support

For issues with the release process:
- Check the workflow logs in the Actions tab
- Review the audit logs in `/tmp/release-audit-*.log` on the runner

For Sonatype Central issues:
- Contact: central-support@sonatype.com
- Status page: https://status.maven.org

---

Copyright 2026-present Douglas Hoard