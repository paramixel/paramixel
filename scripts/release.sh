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
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly PROJECT_DIR
readonly MVNW="${PROJECT_DIR}/mvnw"
readonly GRADLEW="${PROJECT_DIR}/gradlew"

readonly VERSION_REGEX='^[0-9]+\.[0-9]+\.[0-9]+$'

EXECUTE="false"
SKIP_GRADLE="false"
SKIP_DOCS_BUILD="false"
VERSION=""

usage() {
    cat <<'EOF'
Usage: ./scripts/release.sh <version> [OPTIONS]

Automated release script for Paramixel. Performs all release steps except
publishing to the Maven Central Portal (which requires manual verification).

By default runs in dry-run mode (prints commands without executing).
Use --execute to perform the actual release.

The script is idempotent — safe to re-run. Each step detects if it has
already been completed and skips accordingly.

Arguments:
  <version>              Release version in x.y.z format (e.g. 1.2.3)

Options:
  --execute              Execute the release (default is dry-run)
  --skip-gradle          Skip Gradle build validation
  --skip-docs-build      Skip documentation build (use if docs already built)
  -h, --help             Show this help text

Examples:
  ./scripts/release.sh 1.2.3                    # Dry run
  ./scripts/release.sh 1.2.3 --execute          # Execute release
  ./scripts/release.sh 1.2.3 --execute --skip-gradle
EOF
}

log() {
    echo "[INFO] $*"
}

warn() {
    echo "[WARN] $*" >&2
}

fail() {
    echo "[ERROR] $*" >&2
    exit 1
}

prompt_yes() {
    local question="$1"
    local answer
    echo ""
    echo "${question}"
    read -r -p "[y/N] " answer
    echo ""
    [[ "${answer}" == "y" || "${answer}" == "Y" ]]
}

run_cmd() {
    local description="$1"
    shift
    if [[ "${EXECUTE}" == "true" ]]; then
        log "${description}"
        "$@"
    else
        log "[DRY-RUN] $*"
    fi
}

parse_args() {
    if [[ $# -lt 1 ]]; then
        usage
        exit 1
    fi

    VERSION="$1"
    shift

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --execute)
                EXECUTE="true"
                ;;
            --skip-gradle)
                SKIP_GRADLE="true"
                ;;
            --skip-docs-build)
                SKIP_DOCS_BUILD="true"
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                usage >&2
                fail "Unknown argument: $1"
                ;;
        esac
        shift
    done
}

get_current_revision() {
    (cd "${PROJECT_DIR}" && ./mvnw help:evaluate -Dexpression=revision -q -DforceStdout 2>/dev/null) || echo ""
}

get_current_branch() {
    git -C "${PROJECT_DIR}" rev-parse --abbrev-ref HEAD
}

is_working_tree_clean() {
    git -C "${PROJECT_DIR}" status --porcelain | grep -q . && return 1 || return 0
}

is_main_synced() {
    local local_hash remote_hash
    local_hash=$(git -C "${PROJECT_DIR}" rev-parse main)
    remote_hash=$(git -C "${PROJECT_DIR}" rev-parse origin/main 2>/dev/null) || return 1
    [[ "${local_hash}" == "${remote_hash}" ]]
}

local_branch_exists() {
    git -C "${PROJECT_DIR}" rev-parse --verify "$1" &>/dev/null
}

remote_branch_exists() {
    git -C "${PROJECT_DIR}" rev-parse --verify "origin/$1" &>/dev/null
}

local_tag_exists() {
    git -C "${PROJECT_DIR}" rev-parse --verify "$1" &>/dev/null
}

remote_tag_exists() {
    git -C "${PROJECT_DIR}" ls-remote --tags origin "$1" 2>/dev/null | grep -q .
}

tag_points_at() {
    local tag="$1"
    local commit="$2"
    local tag_commit
    tag_commit=$(git -C "${PROJECT_DIR}" rev-list -n 1 "${tag}" 2>/dev/null) || return 1
    [[ "${tag_commit}" == "${commit}" ]]
}

remote_branch_tip_matches() {
    local branch="$1"
    local commit="$2"
    local remote_commit
    remote_commit=$(git -C "${PROJECT_DIR}" rev-parse "origin/${branch}" 2>/dev/null) || return 1
    [[ "${remote_commit}" == "${commit}" ]]
}

preflight_checks() {
    log "Running pre-flight checks..."

    [[ "${VERSION}" =~ ${VERSION_REGEX} ]] || fail "Invalid version '${VERSION}'. Must be x.y.z format (e.g. 1.2.3)."

    [[ "${VERSION}" != *"-POST" ]] || fail "Version must not end with -POST."

    [[ -x "${MVNW}" ]] || fail "mvnw not found or not executable: ${MVNW}"

    if [[ "${SKIP_GRADLE}" == "false" ]]; then
        [[ -x "${GRADLEW}" ]] || fail "gradlew not found or not executable: ${GRADLEW} (use --skip-gradle to skip)"
    fi

    local current_branch
    current_branch=$(get_current_branch)
    [[ "${current_branch}" == "main" ]] || fail "Must be on main branch. Currently on: ${current_branch}"

    is_working_tree_clean || fail "Working tree is not clean. Commit or stash changes before releasing."

    is_main_synced || fail "main is not synced with origin/main. Pull or push first."

    local_branch_exists "release/${VERSION}" && log "Local branch release/${VERSION} already exists (will reuse)"
    remote_branch_exists "release/${VERSION}" && log "Remote branch release/${VERSION} already exists (will reuse)"

    if local_tag_exists "v${VERSION}"; then
        local current_head
        current_head=$(git -C "${PROJECT_DIR}" rev-parse HEAD)
        if tag_points_at "v${VERSION}" "${current_head}"; then
            log "Local tag v${VERSION} already exists at current HEAD (will reuse)"
        else
            local tag_commit
            tag_commit=$(git -C "${PROJECT_DIR}" rev-list -n 1 "v${VERSION}")
            fail "Local tag v${VERSION} exists but points at ${tag_commit}, not current HEAD. Delete it if stale: git tag -d v${VERSION}"
        fi
    fi

    if remote_tag_exists "v${VERSION}"; then
        fail "Remote tag v${VERSION} already exists. Delete if stale: git push origin :refs/tags/v${VERSION}"
    fi

    if [[ "${EXECUTE}" == "true" ]]; then
        if ! echo "test" | gpg --batch --clearsign &>/dev/null; then
            fail "GPG signing is not working. Verify your GPG key and passphrase."
        fi

        if [[ ! -f "${HOME}/.m2/settings.xml" ]]; then
            fail "Maven settings not found: ~/.m2/settings.xml. Create it with your Sonatype Central credentials."
        fi
    fi

    if [[ -z "${WWW_PARAMIXEL_ORG:-}" ]]; then
        fail "WWW_PARAMIXEL_ORG environment variable is not set. Set it to your SSH host alias."
    fi

    log "Pre-flight checks passed."
}

step1_prepare_release_branch() {
    log "========================================"
    log "Step 1 — Prepare release branch"
    log "========================================"

    local current_revision
    current_revision=$(get_current_revision)

    if local_branch_exists "release/${VERSION}"; then
        run_cmd "Checking out existing branch release/${VERSION}" git -C "${PROJECT_DIR}" checkout "release/${VERSION}"
    elif remote_branch_exists "release/${VERSION}"; then
        run_cmd "Tracking existing remote branch release/${VERSION}" git -C "${PROJECT_DIR}" checkout --track "origin/release/${VERSION}"
    else
        run_cmd "Creating branch release/${VERSION} from main" git -C "${PROJECT_DIR}" checkout -b "release/${VERSION}"
    fi

    if [[ "${current_revision}" == "${VERSION}" ]]; then
        log "Version already set to ${VERSION}, skipping versions:set-property"
    else
        run_cmd "Setting version to ${VERSION}" "${MVNW}" versions:set-property -Dproperty=revision -DnewVersion="${VERSION}" -DgenerateBackupPoms=false
    fi

    if [[ "${EXECUTE}" == "true" ]]; then
        run_cmd "Applying code formatting" "${MVNW}" spotless:apply
        run_cmd "Building with Maven" "${MVNW}" clean install

        if [[ "${SKIP_GRADLE}" == "false" ]]; then
            run_cmd "Building with Gradle" "${GRADLEW}" clean check --no-daemon
        else
            log "Skipping Gradle build (--skip-gradle)"
        fi

        run_cmd "Building documentation" "${SCRIPT_DIR}/build-documentation.sh"
    else
        log "[DRY-RUN] ${MVNW} spotless:apply"
        log "[DRY-RUN] ${MVNW} clean install"
        if [[ "${SKIP_GRADLE}" == "false" ]]; then
            log "[DRY-RUN] ${GRADLEW} clean check --no-daemon"
        fi
        log "[DRY-RUN] ${SCRIPT_DIR}/build-documentation.sh"
    fi

    if [[ "${EXECUTE}" == "true" ]]; then
        if git -C "${PROJECT_DIR}" diff --cached --quiet 2>/dev/null && git -C "${PROJECT_DIR}" diff --quiet 2>/dev/null; then
            log "No changes to commit"
        else
            run_cmd "Committing release" git -C "${PROJECT_DIR}" add -A
            run_cmd "Committing release" git -C "${PROJECT_DIR}" commit -s -m "release: Release ${VERSION}"
        fi
    else
        log "[DRY-RUN] git add -A"
        log "[DRY-RUN] git commit -s -m \"release: Release ${VERSION}\""
    fi

    if [[ "${EXECUTE}" == "true" ]]; then
        local local_commit
        local_commit=$(git -C "${PROJECT_DIR}" rev-parse HEAD)
        if remote_branch_exists "release/${VERSION}" && remote_branch_tip_matches "release/${VERSION}" "${local_commit}"; then
            log "Remote branch release/${VERSION} already up to date, skipping push"
        else
            if prompt_yes "Push release/${VERSION} to origin?"; then
                git -C "${PROJECT_DIR}" push -u origin "release/${VERSION}"
            else
                fail "Push cancelled. Release branch is local only."
            fi
        fi
    else
        log "[DRY-RUN] git push -u origin release/${VERSION}"
    fi
}

step2_wait_for_ci() {
    log "========================================"
    log "Step 2 — Wait for CI"
    log "========================================"

    echo ""
    log "CI is running on release/${VERSION}."
    log "Check: https://github.com/paramixel/paramixel/actions"
    echo ""

    if [[ "${EXECUTE}" == "true" ]]; then
        if prompt_yes "Has CI passed on release/${VERSION}?"; then
            log "CI confirmed passed."
        else
            log "CI not passed. To fix on the release branch and retry, push fixes and re-run this script."
            log "To start over:"
            log "  git push origin --delete release/${VERSION}"
            log "  git checkout main"
            log "  git branch -D release/${VERSION}"
            fail "Release aborted — CI not passed."
        fi
    else
        log "[DRY-RUN] Would prompt: Has CI passed on release/${VERSION}?"
    fi
}

step3_deploy_to_maven_central() {
    log "========================================"
    log "Step 3 — Deploy to Maven Central"
    log "========================================"

    if [[ "${EXECUTE}" == "true" ]]; then
        log "Deploying release artifacts to Sonatype Central..."
        (cd "${PROJECT_DIR}" && ./mvnw -Prelease clean deploy)
        log "Deployment complete. Artifacts are pending at Sonatype Central."
    else
        log "[DRY-RUN] ${MVNW} -Prelease clean deploy"
    fi
}

step4_verify_and_publish() {
    log "========================================"
    log "Step 4 — Verify and publish to Maven Central"
    log "========================================"

    echo ""
    log "Verify the pending deployment at https://central.sonatype.com"
    log ""
    log "  1. Confirm the version number is correct: ${VERSION}"
    log "  2. Confirm expected publishable artifacts are present"
    log "  3. Confirm each artifact has JAR, sources, javadoc, and POM files"
    log "  4. Confirm GPG signatures are present"
    log "  5. Confirm Central validation completed successfully"
    log ""
    log "Then publish the deployment from the Maven Central Publishing Portal."
    echo ""

    if [[ "${EXECUTE}" == "true" ]]; then
        if prompt_yes "Have you published the deployment in Maven Central?"; then
            log "Maven Central publish confirmed."
        else
            log "Rolling back..."
            rollback_release
            fail "Release aborted — Maven Central not published. All release artifacts have been cleaned up."
        fi
    else
        log "[DRY-RUN] Would prompt: Have you published the deployment in Maven Central?"
        log "[DRY-RUN] Would rollback if answered N"
    fi
}

rollback_release() {
    log "Rolling back release..."

    if remote_branch_exists "release/${VERSION}"; then
        run_cmd "Deleting remote branch release/${VERSION}" git -C "${PROJECT_DIR}" push origin --delete "release/${VERSION}"
    else
        log "Remote branch release/${VERSION} does not exist, skipping remote delete"
    fi

    local current_branch
    current_branch=$(get_current_branch)
    if [[ "${current_branch}" != "main" ]]; then
        run_cmd "Switching to main" git -C "${PROJECT_DIR}" checkout main
    fi

    if local_branch_exists "release/${VERSION}"; then
        run_cmd "Deleting local branch release/${VERSION}" git -C "${PROJECT_DIR}" branch -D "release/${VERSION}"
    else
        log "Local branch release/${VERSION} does not exist, skipping local delete"
    fi

    echo ""
    warn "Deployment is still pending at https://central.sonatype.com"
    warn "Drop the deployment from the portal if you do not intend to publish."
}

step5_tag_release() {
    log "========================================"
    log "Step 5 — Tag the release"
    log "========================================"

    local tag="v${VERSION}"

    if [[ "${EXECUTE}" == "true" ]]; then
        local current_head
        current_head=$(git -C "${PROJECT_DIR}" rev-parse HEAD)

        if local_tag_exists "${tag}"; then
            if tag_points_at "${tag}" "${current_head}"; then
                log "Tag ${tag} already exists at current HEAD, skipping tag creation"
            else
                local tag_commit
                tag_commit=$(git -C "${PROJECT_DIR}" rev-list -n 1 "${tag}")
                fail "Tag ${tag} exists but points at ${tag_commit}, not HEAD. Delete it if stale: git tag -d ${tag}"
            fi
        else
            run_cmd "Creating tag ${tag}" git -C "${PROJECT_DIR}" tag -a "${tag}" -m "Release ${VERSION}"
        fi

        if remote_tag_exists "${tag}"; then
            log "Remote tag ${tag} already exists, skipping push"
        else
            if prompt_yes "Push tag ${tag} to origin?"; then
                git -C "${PROJECT_DIR}" push origin "${tag}"
            else
                fail "Tag push cancelled. Tag ${tag} exists locally but not on remote."
            fi
        fi
    else
        log "[DRY-RUN] git tag -a ${tag} -m \"Release ${VERSION}\""
        log "[DRY-RUN] git push origin ${tag}"
    fi
}

step6_publish_documentation() {
    log "========================================"
    log "Step 6 — Publish documentation"
    log "========================================"

    local docs_args=()
    if [[ "${SKIP_DOCS_BUILD}" == "true" ]]; then
        docs_args+=(--skip-build)
    fi

    if [[ "${EXECUTE}" == "true" ]]; then
        run_cmd "Publishing documentation" "${SCRIPT_DIR}/publish-documentation.sh" "${docs_args[@]+"${docs_args[@]}"}"
    else
        local cmd="${SCRIPT_DIR}/publish-documentation.sh"
        if [[ "${SKIP_DOCS_BUILD}" == "true" ]]; then
            cmd="${cmd} --skip-build"
        fi
        log "[DRY-RUN] ${cmd}"
    fi
}

step7_bump_dev_version() {
    log "========================================"
    log "Step 7 — Bump main to development version"
    log "========================================"

    local dev_version="${VERSION}-POST"

    if [[ "${EXECUTE}" == "true" ]]; then
        local current_branch
        current_branch=$(get_current_branch)
        if [[ "${current_branch}" != "main" ]]; then
            run_cmd "Switching to main" git -C "${PROJECT_DIR}" checkout main
        fi

        run_cmd "Pulling main" git -C "${PROJECT_DIR}" pull --ff-only

        local current_revision
        current_revision=$(get_current_revision)

        if [[ "${current_revision}" == "${dev_version}" ]]; then
            log "Version already set to ${dev_version}, skipping versions:set-property"
        else
            run_cmd "Setting version to ${dev_version}" "${MVNW}" versions:set-property -Dproperty=revision -DnewVersion="${dev_version}" -DgenerateBackupPoms=false
        fi

        run_cmd "Applying code formatting" "${MVNW}" spotless:apply
        run_cmd "Building with Maven" "${MVNW}" clean install

        if [[ "${SKIP_GRADLE}" == "false" ]]; then
            run_cmd "Building with Gradle" "${GRADLEW}" clean check --no-daemon
        else
            log "Skipping Gradle build (--skip-gradle)"
        fi

        if git -C "${PROJECT_DIR}" diff --cached --quiet 2>/dev/null && git -C "${PROJECT_DIR}" diff --quiet 2>/dev/null; then
            log "No changes to commit"
        else
            run_cmd "Committing dev version bump" git -C "${PROJECT_DIR}" add -A
            run_cmd "Committing dev version bump" git -C "${PROJECT_DIR}" commit -s -m "chore: Prepare for development"
        fi

        local local_commit
        local_commit=$(git -C "${PROJECT_DIR}" rev-parse HEAD)
        local remote_commit
        remote_commit=$(git -C "${PROJECT_DIR}" rev-parse origin/main 2>/dev/null) || remote_commit=""
        if [[ "${local_commit}" == "${remote_commit}" ]]; then
            log "Remote main already up to date, skipping push"
        else
            if prompt_yes "Push main to origin?"; then
                git -C "${PROJECT_DIR}" push
            else
                fail "Push cancelled. Main has dev version bump but is not pushed."
            fi
        fi
    else
        log "[DRY-RUN] git checkout main"
        log "[DRY-RUN] git pull --ff-only"
        log "[DRY-RUN] ${MVNW} versions:set-property -Dproperty=revision -DnewVersion=${dev_version} -DgenerateBackupPoms=false"
        log "[DRY-RUN] ${MVNW} spotless:apply"
        log "[DRY-RUN] ${MVNW} clean install"
        if [[ "${SKIP_GRADLE}" == "false" ]]; then
            log "[DRY-RUN] ${GRADLEW} clean check --no-daemon"
        fi
        log "[DRY-RUN] git add -A"
        log "[DRY-RUN] git commit -s -m \"chore: Prepare for development\""
        log "[DRY-RUN] git push"
    fi
}

main() {
    parse_args "$@"

    if [[ "${EXECUTE}" == "true" ]]; then
        log "========================================="
        log " Paramixel Release ${VERSION} (EXECUTE)"
        log "========================================="
    else
        log "========================================="
        log " Paramixel Release ${VERSION} (DRY-RUN)"
        log "========================================="
        log ""
        log "This is a dry run. No changes will be made."
        log "Use --execute to perform the actual release."
        log ""
    fi

    preflight_checks

    step1_prepare_release_branch
    step2_wait_for_ci
    step3_deploy_to_maven_central
    step4_verify_and_publish
    step5_tag_release
    step6_publish_documentation
    step7_bump_dev_version

    log "========================================="
    if [[ "${EXECUTE}" == "true" ]]; then
        log " Release ${VERSION} completed!"
    else
        log " Dry run completed!"
    fi
    log "========================================="
}

main "$@"
