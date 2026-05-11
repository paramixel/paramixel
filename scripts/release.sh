#!/usr/bin/env bash
#
# Copyright (c) 2026-present Douglas Hoard
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly MVNW="${PROJECT_DIR}/mvnw"

validate_version() {
    local version="$1"

    [[ "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]] || fail "Invalid version format '${version}'. Expected MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-label"

    [[ "${version}" != *-POST ]] || fail "Version must not end in -POST: '${version}'"
}

require_mvnw() {
    [[ -x "${MVNW}" ]] || fail "mvnw not found or not executable: ${MVNW}"
}

require_maven_settings() {
    local settings_file="$HOME/.m2/settings.xml"

    [[ -f "${settings_file}" ]] || fail "Maven settings not found: ${settings_file}. See RELEASING.md for setup instructions."

    grep -q '<id>central</id>' "${settings_file}" || fail "Maven settings at ${settings_file} does not contain a <server> with <id>central</id>. See RELEASING.md for setup instructions."
}

require_gradle_credentials() {
    [[ -n "${GRADLE_PUBLISH_KEY:-}" ]] || fail "GRADLE_PUBLISH_KEY is not set"
    [[ -n "${GRADLE_PUBLISH_SECRET:-}" ]] || fail "GRADLE_PUBLISH_SECRET is not set"
}

confirm() {
    local prompt="$1"
    local answer

    echo ""
    read -r -p "${prompt} [y/N] " answer
    case "${answer}" in
        [yY]|[yY][eE][sS]) return 0 ;;
        *) return 1 ;;
    esac
}

usage() {
    cat <<'EOF'
Release script for Paramixel. Each phase corresponds to a step in the
release process documented in RELEASING.md.

Usage:

  ./scripts/release.sh <phase> [args]

Phases:

  phase1 <version>    Prepare the release branch and set the version.
                      Prints CI wait instructions on completion.

  phase2 <version>    Deploy to Maven Central (Sonatype Central).

  phase3              Verify and publish to Maven Central.
                      Prompts for confirmation before the irreversible
                      publish step.

  phase4 <version>    Tag the release and push the tag.

  phase5 <version>    Bump main to the next development version (<version>-POST).

Arguments:

  <version>    Release version in MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-label format.
               Must not end in -POST.

Prerequisites:

  - Maven Central credentials in ~/.m2/settings.xml
  - GPG signing key published to key servers
  - Clean working tree on main for phase1/phase5

Examples:

  ./scripts/release.sh phase1 3.0.1
  ./scripts/release.sh phase2 3.0.1
  ./scripts/release.sh phase3
  ./scripts/release.sh phase4 3.0.1
  ./scripts/release.sh phase5 3.0.1

EOF
}

log() {
    echo "[INFO] $*"
}

fail() {
    echo "[ERROR] $*" >&2
    exit 1
}

phase1() {
    local version="$1"

    validate_version "${version}"

    log "Phase 1: Preparing release branch for version ${version}"

    (
        cd "${PROJECT_DIR}"

        local current_branch
        current_branch="$(git branch --show-current)"

        if [[ "${current_branch}" != "main" ]]; then
            log "Checking out main"
            git checkout main
        fi

        log "Pulling latest from origin"
        git pull

        log "Creating release branch release/${version}"
        git checkout -b "release/${version}"

        log "Setting version to ${version}"
        ./mvnw versions:set \
            -DnewVersion="${version}" \
            -DprocessAllModules \
            -DgenerateBackupPoms=false

        log "Applying spotless formatting"
        ./mvnw spotless:apply

        log "Syncing Gradle plugin version"
        "${SCRIPT_DIR}/build.sh" sync-gradle-version

        log "Committing release"
        git add -A
        git commit -s -m "Release ${version}"

        log "Pushing release branch to origin"
        git push -u origin "release/${version}"
    )

    log "Phase 1 complete: release/${version} branch created and pushed"
    log ""
    log "CI is now running on the release branch."
    log "Wait for all CI jobs to pass before proceeding to phase 2."
    log "If CI fails, fix the issue on the release branch and push again."
    log "If the fix requires changes on main, delete the release branch and start over."
}

phase2() {
    local version="$1"

    validate_version "${version}"

    log "Phase 2: Deploying to Maven Central"

    (
        cd "${PROJECT_DIR}"

        local current_branch
        current_branch="$(git branch --show-current)"

        if [[ "${current_branch}" != "release/${version}" ]]; then
            log "Checking out release/${version}"
            git checkout "release/${version}"
        fi

        log "Running Maven deploy with release profile"
        ./mvnw -Prelease clean deploy
    )

    log "Phase 2 complete: artifacts uploaded to Sonatype Central"
    log "Deployment is pending and not yet published."
    log "Proceed to phase 3 to verify and publish."
}

phase3() {
    log "Phase 3: Verify and publish to Maven Central"
    log ""
    log "*** Maven Central releases are immutable and cannot be undone or overwritten. ***"
    log ""
    log "Before proceeding, verify the deployment at https://central.sonatype.com:"
    log "  - Confirm the version number is correct"
    log "  - Confirm the artifacts (JAR, sources, javadoc, POM) are present and valid"
    log "  - Confirm GPG signatures are present"
    log ""

    if ! confirm "Have you verified the deployment and want to publish?"; then
        log "Publish cancelled. Retry phase 3 when ready."
        exit 0
    fi

    (
        cd "${PROJECT_DIR}"
        ./mvnw -Prelease central-publishing:publish
    )

    log "Phase 3 complete: deployment published to Maven Central"
}

phase4() {
    local version="$1"

    validate_version "${version}"

    log "Phase 4: Tagging release v${version}"

    (
        cd "${PROJECT_DIR}"

        log "Creating annotated tag v${version}"
        git tag -a "v${version}" -m "Release ${version}"

        log "Pushing tag v${version} to origin"
        git push origin "v${version}"
    )

    log "Phase 4 complete: tag v${version} created and pushed"
}

phase5() {
    local version="$1"

    validate_version "${version}"

    local post_version="${version}-POST"

    log "Phase 5: Bumping main to development version ${post_version}"

    (
        cd "${PROJECT_DIR}"

        log "Checking out main"
        git checkout main

        log "Pulling latest from origin"
        git pull

        log "Setting version to ${post_version}"
        ./mvnw versions:set \
            -DnewVersion="${post_version}" \
            -DprocessAllModules \
            -DgenerateBackupPoms=false

        log "Applying spotless formatting"
        ./mvnw spotless:apply

        log "Syncing Gradle plugin version"
        "${SCRIPT_DIR}/build.sh" sync-gradle-version

        log "Running clean install"
        ./mvnw clean install

        log "Committing development version bump"
        git add -A
        git commit -s -m "Prepare for development"

        log "Pushing to origin"
        git push
    )

    log "Phase 5 complete: main bumped to ${post_version}"
}

# DISABLED: Publish the Gradle plugin to the Gradle Plugin Portal.
# Re-enable by adding "phase6" to the case statement and usage text.
publish_gradle_plugin() {
    local java_17_home="$1"

    [[ -d "${java_17_home}" ]] || fail "JAVA_17_HOME directory does not exist: ${java_17_home}"
    [[ -x "${java_17_home}/bin/java" ]] || fail "JAVA_17_HOME/bin/java not executable: ${java_17_home}/bin/java"

    require_gradle_credentials

    log "Publishing Gradle plugin to Gradle Plugin Portal"

    (
        cd "${PROJECT_DIR}"
        JAVA_17_HOME="${java_17_home}" "${SCRIPT_DIR}/build.sh" gradle-plugin
    )

    (
        cd "${PROJECT_DIR}/gradle-plugin"
        GRADLE_PUBLISH_KEY="${GRADLE_PUBLISH_KEY}" \
        GRADLE_PUBLISH_SECRET="${GRADLE_PUBLISH_SECRET}" \
        JAVA_HOME="${java_17_home}" \
            ./gradlew publishPlugins --no-daemon
    )

    log "Gradle plugin published to Gradle Plugin Portal"
}

main() {
    if [[ $# -lt 1 ]]; then
        usage
        exit 1
    fi

    local phase="$1"
    shift

    require_mvnw
    require_maven_settings

    case "${phase}" in
        phase1)
            [[ $# -ge 1 ]] || fail "phase1 requires a version argument"
            phase1 "$1"
            ;;
        phase2)
            [[ $# -ge 1 ]] || fail "phase2 requires a version argument"
            phase2 "$1"
            ;;
        phase3)
            phase3
            ;;
        phase4)
            [[ $# -ge 1 ]] || fail "phase4 requires a version argument"
            phase4 "$1"
            ;;
        phase5)
            [[ $# -ge 1 ]] || fail "phase5 requires a version argument"
            phase5 "$1"
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            fail "Unknown phase: ${phase}"
            ;;
    esac
}

main "$@"
