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
readonly PROJECT_NAME='Paramixel'
readonly DOCS_HOST_VARIABLE='WWW_PARAMIXEL_ORG'
readonly HAS_GRADLE='true'
readonly VERSION_REGEX='^[0-9]+\.[0-9]+\.[0-9]+$'

EXECUTE=false
SKIP_DOCS_BUILD=false
SKIP_GRADLE=false
RETRY_DEPLOY=false
VERSION=''
NEXT_STEP=1
RELEASE_COMMIT=''
STATE_FILE=''
LOCK_DIR=''
REMOTE_TAG_COMMIT=''

usage() {
    cat <<EOF_HELP
Usage: ./scripts/release.sh <version> [OPTIONS]

Release ${PROJECT_NAME}; Central publication requires manual portal verification.
Default: offline dry run. Use --execute to run or resume a release.

Options:
  --execute          Execute, resuming the local checkpoint when present
  --skip-docs-build  Reuse website/build without building documentation
  --skip-gradle      Skip Gradle build validation
  --retry-deploy     Retry an uncertain deployment after checking/dropping it in Central
  -h, --help         Show help (no version required)

Checkpoints live in the Git common directory under release-state/<version>.
Do not delete checkpoints or release tags to retry a published release.
EOF_HELP
}

log() { echo "[INFO] $*"; }
fail() { echo "[ERROR] $*" >&2; exit 1; }
prompt_yes() {
    local answer
    read -r -p "$1 [y/N] " answer || return 1
    [[ "$answer" == y || "$answer" == Y ]]
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --execute) EXECUTE=true ;;
            --skip-docs-build) SKIP_DOCS_BUILD=true ;;
            --skip-gradle)
                [[ "$HAS_GRADLE" == true ]] || fail "Unknown argument: $1"
                SKIP_GRADLE=true
                ;;
            --retry-deploy) RETRY_DEPLOY=true ;;
            -h|--help) usage; exit 0 ;;
            -*) fail "Unknown argument: $1" ;;
            *)
                [[ -z "$VERSION" ]] || fail "Unexpected argument: $1"
                VERSION="$1"
                ;;
        esac
        shift
    done
    [[ "$VERSION" =~ $VERSION_REGEX ]] || fail "Version must be x.y.z (for example 1.2.3)."
}

# Use exact namespaces: a branch must never be mistaken for a tag (or vice versa).
ref_exists() { git show-ref --verify --quiet "$1"; }
commit_at() { git rev-parse --verify "$1^{commit}"; }
require_clean_tree() {
    local status
    status=$(git status --porcelain) || fail "Cannot inspect working tree."
    [[ -z "$status" ]] || fail "Working tree is not clean. Review and commit or stash changes, then rerun."
}

read_remote_tag() {
    local refs direct peeled
    refs=$(git ls-remote --tags origin "refs/tags/v${VERSION}" "refs/tags/v${VERSION}^{}") \
        || fail "Cannot query remote tags; check origin connectivity and credentials."
    direct=$(awk -v ref="refs/tags/v${VERSION}" '$2 == ref { print $1 }' <<< "$refs")
    peeled=$(awk -v ref="refs/tags/v${VERSION}^{}" '$2 == ref { print $1 }' <<< "$refs")
    REMOTE_TAG_COMMIT="${peeled:-$direct}"
}

load_state() {
    local extra=''
    if [[ -f "$STATE_FILE" ]]; then
        {
            read -r NEXT_STEP && read -r RELEASE_COMMIT && ! read -r extra && [[ -z "$extra" ]]
        } < "$STATE_FILE" || fail "Invalid checkpoint: $STATE_FILE"
        [[ "$NEXT_STEP" =~ ^[1-8]$ ]] || fail "Invalid checkpoint step: $STATE_FILE"
        [[ -z "$RELEASE_COMMIT" || "$RELEASE_COMMIT" =~ ^[0-9a-f]{40,64}$ ]] \
            || fail "Invalid checkpoint commit: $STATE_FILE"
        [[ "$NEXT_STEP" == 1 || -n "$RELEASE_COMMIT" ]] || fail "Missing checkpoint commit."
    fi
}

save_state() {
    NEXT_STEP="$1"
    printf '%s\n%s\n' "$NEXT_STEP" "$RELEASE_COMMIT" > "${STATE_FILE}.tmp"
    mv "${STATE_FILE}.tmp" "$STATE_FILE"
}

get_current_revision() {
    local revision
    revision=$(./mvnw help:evaluate -Dexpression=revision -q -DforceStdout -Dstyle.color=never) \
        || fail "Cannot read Maven revision."
    # Some Maven versions emit ANSI reset sequences even with color disabled.
    revision=$(printf '%s' "$revision" | sed $'s/\033\\[[0-9;]*m//g')
    [[ "$revision" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-POST)?$ ]] || fail "Unexpected Maven revision: $revision"
    printf '%s\n' "$revision"
}

validate_build() {
    ./mvnw spotless:apply
    ./mvnw clean install
    if [[ "$HAS_GRADLE" == true && "$SKIP_GRADLE" == false ]]; then
        ./mvnw spotless:apply
        ./gradlew clean check --no-daemon
    fi
}

validate_docs_build() {
    [[ -d website/build && -n "$(find website/build -type f -print -quit)" ]] \
        || fail "website/build is missing or empty; build the release documentation first."
}

preflight_checks() {
    require_clean_tree
    git fetch --prune origin '+refs/heads/*:refs/remotes/origin/*' \
        || fail "Cannot refresh origin branches."
    read_remote_tag

    local branch main_commit remote_main_commit
    branch=$(git symbolic-ref --quiet --short HEAD) || fail "Detached HEAD is not supported."
    if [[ "$NEXT_STEP" == 1 ]]; then
        [[ "$branch" == main || "$branch" == "release/${VERSION}" ]] || fail "Start on main or release/${VERSION}."
        main_commit=$(commit_at refs/heads/main) || fail "Local main branch is missing."
        remote_main_commit=$(commit_at refs/remotes/origin/main) || fail "Origin main branch is missing."
        [[ "$main_commit" == "$remote_main_commit" ]] || fail "main is not synced with origin/main. Pull or push first."
        if ref_exists "refs/tags/v${VERSION}" || [[ -n "$REMOTE_TAG_COMMIT" ]]; then
            fail "Tag v${VERSION} exists without a prepared checkpoint. Recover manually; do not delete a published tag."
        fi
    elif [[ "$NEXT_STEP" -lt 7 ]]; then
        [[ "$branch" == main || "$branch" == "release/${VERSION}" ]] || fail "Resume on main or release/${VERSION}."
    else
        [[ "$branch" == main || "$branch" == "release/${VERSION}" ]] || fail "Resume the development bump on main or release/${VERSION}."
    fi

    if [[ "$NEXT_STEP" -gt 1 ]]; then
        if ref_exists "refs/tags/v${VERSION}"; then
            [[ "$(commit_at "refs/tags/v${VERSION}")" == "$RELEASE_COMMIT" ]] || fail "Local release tag conflicts with checkpoint."
        fi
        [[ -z "$REMOTE_TAG_COMMIT" || "$REMOTE_TAG_COMMIT" == "$RELEASE_COMMIT" ]] || fail "Remote release tag conflicts with checkpoint."
        [[ "$NEXT_STEP" -gt 4 || -z "$REMOTE_TAG_COMMIT" ]] || fail "Remote tag exists before publication confirmation; recover manually."
        [[ "$NEXT_STEP" -lt 6 || "$REMOTE_TAG_COMMIT" == "$RELEASE_COMMIT" ]] || fail "Published release tag is missing on origin."
    fi

    if [[ "$NEXT_STEP" -le 6 ]]; then
        [[ -n "${!DOCS_HOST_VARIABLE:-}" ]] || fail "Set ${DOCS_HOST_VARIABLE} to the documentation SSH host alias."
        local dependency
        for dependency in rsync ssh; do
            command -v "$dependency" >/dev/null || fail "Required command missing: $dependency"
        done
    fi
    if [[ "$NEXT_STEP" -le 3 || "$RETRY_DEPLOY" == true ]]; then
        [[ -f "${HOME}/.m2/settings.xml" ]] || fail "Maven settings not found: ~/.m2/settings.xml"
        echo test | gpg --batch --clearsign >/dev/null 2>&1 || fail "GPG signing is not working."
    fi
    [[ -x ./mvnw ]] || fail "mvnw is not executable."
    if [[ "$HAS_GRADLE" == true && "$SKIP_GRADLE" == false && ( "$NEXT_STEP" == 1 || "$NEXT_STEP" == 7 ) ]]; then
        [[ -x ./gradlew ]] || fail "gradlew is not executable (or use --skip-gradle)."
    fi
}

prepare_release() {
    log 'Step 1 — Prepare release branch'
    if ref_exists "refs/heads/release/${VERSION}"; then
        git checkout "release/${VERSION}"
    elif ref_exists "refs/remotes/origin/release/${VERSION}"; then
        git checkout --track "origin/release/${VERSION}"
    else
        git checkout -b "release/${VERSION}" main
    fi
    local revision
    revision=$(get_current_revision)
    if [[ "$revision" != "$VERSION" ]]; then
        ./mvnw versions:set-property -Dproperty=revision -DnewVersion="$VERSION" -DgenerateBackupPoms=false
    fi
    validate_build
    if [[ "$SKIP_DOCS_BUILD" == false ]]; then
        "${SCRIPT_DIR}/build-documentation.sh"
    fi
    validate_docs_build
    commit_changes "chore: Release ${VERSION}"
    RELEASE_COMMIT=$(commit_at HEAD)
    push_release_branch
}

commit_changes() {
    if [[ -n "$(git status --porcelain)" ]]; then
        git add -A
        git commit -s -m "$1"
    fi
}

push_release_branch() {
    if ! ref_exists "refs/remotes/origin/release/${VERSION}" \
        || [[ "$(commit_at "refs/remotes/origin/release/${VERSION}")" != "$(commit_at HEAD)" ]]; then
        prompt_yes "Push release/${VERSION} to origin?" || fail 'Push cancelled; rerun to resume.'
        git push -u origin "refs/heads/release/${VERSION}:refs/heads/release/${VERSION}"
    fi
}

checkout_release() {
    git checkout "release/${VERSION}"
    require_clean_tree
    local revision
    revision=$(get_current_revision)
    [[ "$revision" == "$VERSION" ]] || fail "Release branch revision does not match ${VERSION}."
    [[ "$(commit_at HEAD)" == "$RELEASE_COMMIT" ]] || fail "Release commit changed after CI confirmation; recover manually."
    local remote_refs remote_commit
    remote_refs=$(git ls-remote --heads origin "refs/heads/release/${VERSION}") || fail "Cannot query origin release branch."
    remote_commit=$(awk '{ print $1 }' <<< "$remote_refs")
    [[ "$remote_commit" == "$RELEASE_COMMIT" ]] || fail "Origin release branch does not match the release commit."
}

wait_for_ci() {
    log 'Step 2 — Confirm CI'
    git checkout "release/${VERSION}"
    local revision
    revision=$(get_current_revision)
    [[ "$revision" == "$VERSION" ]] || fail 'Release revision changed; correct it before continuing.'
    push_release_branch
    RELEASE_COMMIT=$(commit_at HEAD)
    log "Check https://github.com/paramixel/paramixel/actions for commit ${RELEASE_COMMIT}."
    prompt_yes 'Has CI passed for this exact commit?' || fail 'CI not confirmed. Push fixes on the release branch and rerun.'
}

deploy_to_central() {
    log 'Step 3 — Deploy to Maven Central'
    checkout_release
    ./mvnw spotless:apply
    require_clean_tree
    read_remote_tag
    [[ -z "$REMOTE_TAG_COMMIT" ]] || fail "Remote release tag appeared before deployment; recover manually."
    # A failing client may already have uploaded. Never silently deploy again.
    save_state 4
    ./mvnw -Prelease clean deploy || fail 'Deployment outcome is uncertain. Inspect Central, then rerun to confirm publication or use --retry-deploy after dropping any pending deployment.'
    require_clean_tree
}

verify_publication() {
    log 'Step 4 — Verify and publish in https://central.sonatype.com'
    checkout_release
    log "Verify version ${VERSION}, expected artifacts, JARs, sources, Javadoc, POMs, signatures, and successful validation."
    log 'Publish from the portal. If the previous deploy failed, inspect the portal before retrying.'
    prompt_yes 'Have you verified and published this deployment in Maven Central?' \
        || fail 'Publication not confirmed. Branches and checkpoint retained; rerun when ready. No deployment was deleted.'
}

tag_release() {
    log 'Step 5 — Tag release'
    checkout_release
    read_remote_tag
    [[ -z "$REMOTE_TAG_COMMIT" || "$REMOTE_TAG_COMMIT" == "$RELEASE_COMMIT" ]] || fail 'Remote release tag conflicts with release commit.'
    if ref_exists "refs/tags/v${VERSION}"; then
        [[ "$(commit_at "refs/tags/v${VERSION}")" == "$RELEASE_COMMIT" ]] || fail 'Local release tag conflicts with release commit.'
    else
        git tag -a "v${VERSION}" "$RELEASE_COMMIT" -m "Release ${VERSION}"
    fi
    if [[ -z "$REMOTE_TAG_COMMIT" ]]; then
        prompt_yes "Push tag v${VERSION} to origin?" || fail 'Tag push cancelled; rerun to resume.'
        git push origin "refs/tags/v${VERSION}:refs/tags/v${VERSION}"
    fi
}

publish_docs() {
    log 'Step 6 — Publish documentation'
    checkout_release
    # An interrupted run may have lost ignored build output. Rebuild for this commit.
    if [[ "$SKIP_DOCS_BUILD" == false && "$PREPARED_THIS_RUN" == false ]]; then
        "${SCRIPT_DIR}/build-documentation.sh"
    fi
    validate_docs_build
    require_clean_tree
    "${SCRIPT_DIR}/publish-documentation.sh" --skip-build
}

bump_dev_version() {
    log 'Step 7 — Bump development version'
    git checkout main
    git pull --ff-only origin main
    local revision
    revision=$(get_current_revision)
    if [[ "$revision" != "${VERSION}-POST" ]]; then
        ./mvnw versions:set-property -Dproperty=revision -DnewVersion="${VERSION}-POST" -DgenerateBackupPoms=false
    fi
    validate_build
    commit_changes 'chore: Prepare for development'
    if [[ "$(commit_at HEAD)" != "$(commit_at refs/remotes/origin/main)" ]]; then
        prompt_yes 'Push main to origin?' || fail 'Push cancelled; rerun to resume the development bump.'
        git push origin refs/heads/main:refs/heads/main
    fi
}

print_dry_run() {
    log "${PROJECT_NAME} ${VERSION}: offline dry run, next step ${NEXT_STEP}."
    log 'Execution refreshes origin and validates prerequisites and the checkpoint.'
    log '1. Checkout release branch; set revision; spotless:apply; clean install.'
    if [[ "$HAS_GRADLE" == true && "$SKIP_GRADLE" == false ]]; then
        log '   spotless:apply; gradlew clean check --no-daemon (also at step 7).'
    fi
    if [[ "$SKIP_DOCS_BUILD" == true ]]; then
        log '   Reuse website/build (--skip-docs-build).'
    else
        log '   Build documentation.'
    fi
    log '   Commit; confirm release-branch push.'
    log '2. Confirm CI for the exact release commit.'
    log '3. spotless:apply; verify clean tree; checkpoint; mvnw -Prelease clean deploy.'
    log '4. Verify and manually publish in Central; confirm publication.'
    log '5. Create/reuse matching tag; confirm tag push.'
    log '6. Publish documentation using the project documentation host variable.'
    log "7. Set main revision to ${VERSION}-POST; spotless:apply; clean install; commit; confirm push."
    log 'Completed steps are skipped during execution. No commands above were executed.'
}

main() {
    parse_args "$@"
    cd "$PROJECT_DIR"
    local git_common
    git_common=$(git rev-parse --git-common-dir) || fail 'Not a Git repository.'
    git_common=$(cd "$git_common" && pwd)
    STATE_FILE="${git_common}/release-state/${VERSION}"
    if [[ "$EXECUTE" == false ]]; then
        load_state
        print_dry_run
        return
    fi

    LOCK_DIR="${git_common}/release-script.lock"
    mkdir "$LOCK_DIR" 2>/dev/null || fail "Release lock exists: ${LOCK_DIR}. Remove it only after confirming no release process is running."
    trap 'rmdir "$LOCK_DIR"' EXIT
    mkdir -p "${git_common}/release-state"
    load_state
    if [[ "$NEXT_STEP" == 8 ]]; then
        log "Release ${VERSION} already completed."
        return
    fi
    if [[ "$RETRY_DEPLOY" == true && "$NEXT_STEP" != 4 ]]; then
        fail '--retry-deploy is only valid for an uncertain/pending deployment at step 4.'
    fi
    preflight_checks
    if [[ "$RETRY_DEPLOY" == true ]]; then
        checkout_release
        prompt_yes 'Have you confirmed this version is NOT published and dropped ALL pending deployments for it in Central?' \
            || fail 'Deployment retry cancelled.'
        save_state 3
    fi

    PREPARED_THIS_RUN=false
    while [[ "$NEXT_STEP" -le 7 ]]; do
        local step="$NEXT_STEP"
        case "$step" in
            1) save_state 1; prepare_release; PREPARED_THIS_RUN=true ;;
            2) wait_for_ci ;;
            3) deploy_to_central ;;
            4) verify_publication ;;
            5) tag_release ;;
            6) publish_docs ;;
            7) bump_dev_version ;;
        esac
        save_state "$((step + 1))"
    done
    log "Release ${VERSION} completed."
}

main "$@"
