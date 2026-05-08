#!/usr/bin/env bash
set -euo pipefail
set +x

readonly VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9._-]+)?$'
readonly BASE_BRANCH='main'
readonly DEFAULT_NEXT_SUFFIX='POST'
readonly DEFAULT_GRADLE_PLUGIN_DIR='gradle-plugin'
readonly SETTINGS_FILE="${HOME}/.m2/settings.xml"

VERSION=""
NEXT_VERSION=""
MODE="dry-run"
YES="false"
GRADLE_PLUGIN_DIR="${DEFAULT_GRADLE_PLUGIN_DIR}"

ORIGINAL_BRANCH=""
CREATED_RELEASE_BRANCH="false"

usage() {
  cat >&2 <<EOF
Usage:
  ./release.sh --version <version> [options]

Options:
  --version <version>          Release version, e.g. 2.1.0
  --next-version <version>     Next version. Defaults to <version>-POST
  --gradle-plugin-dir <dir>    Gradle plugin directory. Default: ${DEFAULT_GRADLE_PLUGIN_DIR}
  --dry-run                    Validate only. Default.
  --deploy                     Publish release.
  --yes, -y                    Skip deploy confirmation.
  --help, -h                   Show help.

Required env:
  JAVA_17_HOME=/path/to/jdk17
  JAVA_21_HOME=/path/to/jdk21
  JAVA_25_HOME=/path/to/jdk25
EOF
}

log() { echo "[INFO] $*"; }

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

require_arg_value() {
  local option="$1"
  local value="${2:-}"

  [[ -n "${value}" && "${value}" != --* ]] || fail "${option} requires a value"
}

cleanup_on_error() {
  local exit_code=$?

  if [[ "${exit_code}" -ne 0 ]]; then
    echo "[ERROR] Release failed." >&2

    if [[ "${MODE}" == "dry-run" ]]; then
      echo "[INFO] Cleaning tracked dry-run changes only." >&2

      git reset --hard HEAD >/dev/null 2>&1 || true

      if [[ -n "${ORIGINAL_BRANCH}" ]]; then
        git checkout "${ORIGINAL_BRANCH}" >/dev/null 2>&1 || true
      fi

      if [[ "${CREATED_RELEASE_BRANCH}" == "true" ]]; then
        git branch -D "release/${VERSION}" >/dev/null 2>&1 || true
      fi
    else
      echo "[INFO] Deploy mode failed. Local state was left intact for inspection/recovery." >&2
    fi
  fi

  exit "${exit_code}"
}

trap cleanup_on_error EXIT

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --version)
        require_arg_value "$1" "${2:-}"
        VERSION="${2:-}"
        shift 2
        ;;
      --next-version)
        require_arg_value "$1" "${2:-}"
        NEXT_VERSION="${2:-}"
        shift 2
        ;;
      --gradle-plugin-dir)
        require_arg_value "$1" "${2:-}"
        GRADLE_PLUGIN_DIR="${2:-}"
        shift 2
        ;;
      --dry-run)
        MODE="dry-run"
        shift
        ;;
      --deploy)
        MODE="deploy"
        shift
        ;;
      --yes|-y)
        YES="true"
        shift
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        fail "Unknown argument: $1"
        ;;
    esac
  done

  [[ -n "${VERSION}" ]] || fail "--version is required"

  if [[ -z "${NEXT_VERSION}" ]]; then
    NEXT_VERSION="${VERSION}-${DEFAULT_NEXT_SUFFIX}"
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_java_home() {
  local name="$1"
  local home="$2"

  [[ -n "${home}" ]] || fail "${name} is not set"
  [[ -x "${home}/bin/java" ]] || fail "${name}/bin/java not executable: ${home}/bin/java"
}

require_clean_worktree() {
  local status
  status="$(git status --porcelain)"

  [[ -z "${status}" ]] || {
    echo "[ERROR] Working tree is not clean:" >&2
    echo "${status}" >&2
    exit 1
  }
}

require_main_branch() {
  local branch
  branch="$(git branch --show-current)"
  [[ "${branch}" == "${BASE_BRANCH}" ]] || fail "Current branch is '${branch}', expected '${BASE_BRANCH}'"
}

require_gradle_plugin_dir() {
  [[ -d "${GRADLE_PLUGIN_DIR}" ]] || fail "--gradle-plugin-dir is not a directory: ${GRADLE_PLUGIN_DIR}"
  [[ -x "${GRADLE_PLUGIN_DIR}/gradlew" ]] || fail "gradlew not executable: ${GRADLE_PLUGIN_DIR}/gradlew"
  [[ -x "${GRADLE_PLUGIN_DIR}/build.sh" ]] || fail "build.sh not executable: ${GRADLE_PLUGIN_DIR}/build.sh"
}

require_settings_xml() {
  [[ -f "${SETTINGS_FILE}" ]] || fail "Maven settings not found: ${SETTINGS_FILE}"
}

require_gpg_signing() {
  require_command gpg

  log "Checking local GPG signing"
  echo "paramixel-release-preflight" | gpg --batch --clearsign >/dev/null 2>&1 ||
    fail "GPG signing failed. Check your GPG key, agent, and passphrase setup."
}

detect_remote() {
  local remote
  remote="$(git config --get "branch.${BASE_BRANCH}.remote" || true)"

  if [[ -n "${remote}" ]]; then
    echo "${remote}"
  else
    echo "origin"
  fi
}

require_main_synced() {
  local remote="$1"
  local counts
  local ahead
  local behind

  git fetch "${remote}" "${BASE_BRANCH}" --tags

  counts="$(git rev-list --left-right --count "HEAD...${remote}/${BASE_BRANCH}")"
  ahead="$(echo "${counts}" | awk '{print $1}')"
  behind="$(echo "${counts}" | awk '{print $2}')"

  [[ "${ahead}" == "0" && "${behind}" == "0" ]] ||
    fail "${BASE_BRANCH} is not synced with ${remote}/${BASE_BRANCH}; ahead=${ahead}, behind=${behind}"
}

require_no_existing_release_refs() {
  local remote="$1"
  local release_branch="$2"
  local tag_name="$3"

  if git rev-parse -q --verify "refs/heads/${release_branch}" >/dev/null 2>&1; then
    fail "Local release branch already exists: ${release_branch}"
  fi

  if git rev-parse -q --verify "refs/tags/${tag_name}" >/dev/null 2>&1; then
    fail "Local tag already exists: ${tag_name}"
  fi

  if git ls-remote --exit-code --heads "${remote}" "refs/heads/${release_branch}" >/dev/null 2>&1; then
    fail "Remote release branch already exists: ${release_branch}"
  fi

  if git ls-remote --exit-code --tags "${remote}" "refs/tags/${tag_name}" >/dev/null 2>&1; then
    fail "Remote tag already exists: ${tag_name}"
  fi
}

confirm_deploy() {
  if [[ "${MODE}" != "deploy" || "${YES}" == "true" ]]; then
    return 0
  fi

  echo
  echo "About to deploy release ${VERSION}."
  echo "This will publish artifacts, push release branch/tag, and update ${BASE_BRANCH}."
  echo
  read -r -p "Type '${VERSION}' to continue: " confirmation

  [[ "${confirmation}" == "${VERSION}" ]] || fail "Deploy confirmation failed"
}

run_maven() {
  local java_home="$1"
  shift

  JAVA_HOME="${java_home}" \
  PATH="${java_home}/bin:${PATH}" \
  ./mvnw -B "$@"
}

run_gradle() {
  local java_home="$1"
  shift

  (
    cd "${GRADLE_PLUGIN_DIR}"
    JAVA_HOME="${java_home}" \
    PATH="${java_home}/bin:${PATH}" \
    ./gradlew "$@"
  )
}

set_project_version() {
  local java_home="$1"
  local version="$2"

  run_maven "${java_home}" versions:set \
    -DnewVersion="${version}" \
    -DprocessAllModules \
    -DgenerateBackupPoms=false
}

verify_java_25() {
  log "Verifying Maven build with Java 25"
  run_maven "${JAVA_25_HOME}" clean verify \
    -Dspotbugs.skip=true \
    -Dpmd.skip=true
}

verify_java_21() {
  log "Verifying Maven build with Java 21"
  run_maven "${JAVA_21_HOME}" clean verify
}

verify_java_17() {
  log "Verifying Maven build with Java 17"
  run_maven "${JAVA_17_HOME}" clean verify
}

install_release_artifacts_java_17() {
  log "Installing signed release artifacts locally with Java 17"
  run_maven "${JAVA_17_HOME}" -Prelease clean install
}

deploy_release_java_17() {
  log "Deploying release artifacts with Java 17"
  run_maven "${JAVA_17_HOME}" -Prelease clean deploy
}

preflight_push_release_refs() {
  local remote="$1"
  local release_branch="$2"
  local tag_name="$3"

  log "Checking push permissions for release branch and tag"
  git push --dry-run "${remote}" "HEAD:refs/heads/${release_branch}"
  git push --dry-run "${remote}" "refs/tags/${tag_name}:refs/tags/${tag_name}"
}

main() {
  parse_args "$@"

  local release_branch="release/${VERSION}"
  local tag_name="v${VERSION}"
  local remote

  [[ "${MODE}" == "dry-run" || "${MODE}" == "deploy" ]] || fail "Mode must be dry-run or deploy"
  [[ "${VERSION}" =~ ${VERSION_PATTERN} ]] || fail "Invalid --version: ${VERSION}"
  [[ "${NEXT_VERSION}" =~ ${VERSION_PATTERN} ]] || fail "Invalid --next-version: ${NEXT_VERSION}"
  [[ "${VERSION}" != "${NEXT_VERSION}" ]] || fail "--next-version must differ from --version"

  require_command git
  require_command awk

  [[ -f mvnw ]] || fail "mvnw not found in current directory"
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "Not a git repository"

  require_java_home JAVA_17_HOME "${JAVA_17_HOME:-}"
  require_java_home JAVA_21_HOME "${JAVA_21_HOME:-}"
  require_java_home JAVA_25_HOME "${JAVA_25_HOME:-}"
  require_gradle_plugin_dir

  require_clean_worktree
  require_main_branch

  remote="$(detect_remote)"
  ORIGINAL_BRANCH="$(git branch --show-current)"

  require_main_synced "${remote}"
  require_no_existing_release_refs "${remote}" "${release_branch}" "${tag_name}"

  if [[ "${MODE}" == "deploy" ]]; then
    require_settings_xml
    require_gpg_signing
  else
    log "Dry run mode: Maven Central deploy settings are not required"
  fi

  confirm_deploy

  log "Pre-release validation on ${BASE_BRANCH}"
  verify_java_25
  verify_java_21
  verify_java_17

  log "Creating release branch ${release_branch}"
  git checkout -b "${release_branch}"
  CREATED_RELEASE_BRANCH="true"

  log "Setting release version ${VERSION}"
  set_project_version "${JAVA_17_HOME}" "${VERSION}"

  log "Validating release branch"
  verify_java_25
  verify_java_21
  verify_java_17
  install_release_artifacts_java_17
  log "Building Gradle plugin with version ${VERSION}"
  JAVA_HOME="${JAVA_17_HOME}" "${GRADLE_PLUGIN_DIR}/build.sh"

  if [[ "${MODE}" == "dry-run" ]]; then
    log "Dry run complete. No deploy, commits, tags, or pushes were made."

    git reset --hard HEAD
    git checkout "${ORIGINAL_BRANCH}"
    git branch -D "${release_branch}"
    CREATED_RELEASE_BRANCH="false"

    exit 0
  fi

  git add -A
  git commit -s -m "Release ${VERSION}"

  log "Creating local tag ${tag_name}"
  git tag -a "${tag_name}" -m "Release ${VERSION}"

  preflight_push_release_refs "${remote}" "${release_branch}" "${tag_name}"

  deploy_release_java_17

  log "Pushing release branch ${release_branch}"
  git push "${remote}" "${release_branch}"

  log "Pushing tag ${tag_name}"
  git push "${remote}" "${tag_name}"

  log "Switching back to ${BASE_BRANCH}"
  git checkout "${BASE_BRANCH}"

  log "Setting next development version ${NEXT_VERSION}"
  set_project_version "${JAVA_17_HOME}" "${NEXT_VERSION}"

  log "Validating next development build"
  verify_java_17

  log "Syncing Gradle plugin version to ${NEXT_VERSION}"
  JAVA_HOME="${JAVA_17_HOME}" "${GRADLE_PLUGIN_DIR}/build.sh" --sync-version

  git add -A
  git commit -s -m "Prepare for development"

  log "Pushing ${BASE_BRANCH}"
  git push "${remote}" "${BASE_BRANCH}"

  CREATED_RELEASE_BRANCH="false"
  log "Release complete: ${VERSION}"
}

main "$@"
