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
WEBSITE_DIR="${PROJECT_DIR}/website"
readonly WEBSITE_DIR
BUILD_DIR="${WEBSITE_DIR}/build"
readonly BUILD_DIR

SSH_HOST="${WWW_PARAMIXEL_ORG:-}"
DEPLOY_PATH="${DEPLOY_PATH:-/var/www/html/paramixel}"
SKIP_BUILD="false"
DRY_RUN="false"

usage() {
    cat <<'EOF'
Usage: ./scripts/publish-documentation.sh [OPTIONS]

Build and publish the Paramixel documentation site to a remote server via rsync over SSH.

The SSH connection uses your ~/.ssh/config aliases.

Requires the WWW_PARAMIXEL_ORG environment variable to be set (or use --ssh-host).

Options:
  --ssh-host <host>      SSH host alias from ~/.ssh/config.
                          Overrides WWW_PARAMIXEL_ORG env var.
   --deploy-path <path>   Remote directory to deploy to on the server.
                          Default: /var/www/html/paramixel (or DEPLOY_PATH env var).
  --skip-build           Skip the build step (use if docs were already built).
  --dry-run              Show what would be deployed without transferring files.
  -h, --help             Show this help text.

Examples:
  ./scripts/publish-documentation.sh
  ./scripts/publish-documentation.sh --ssh-host my-server
  ./scripts/publish-documentation.sh --deploy-path /var/www/docs
  ./scripts/publish-documentation.sh --skip-build --dry-run
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

check_dependencies() {
    if ! command -v rsync &>/dev/null; then
        fail "rsync is not installed. Please install rsync."
    fi

    if ! command -v ssh &>/dev/null; then
        fail "ssh is not installed. Please install openssh-client."
    fi

    if [[ -z "${SSH_HOST}" ]]; then
        fail "SSH host is required. Set WWW_PARAMIXEL_ORG or pass --ssh-host <host>."
    fi
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --ssh-host)
                [[ -n "${2:-}" && "${2:-}" != --* ]] || fail "--ssh-host requires a value"
                SSH_HOST="$2"
                shift
                ;;
            --deploy-path)
                [[ -n "${2:-}" && "${2:-}" != --* ]] || fail "--deploy-path requires a value"
                DEPLOY_PATH="$2"
                shift
                ;;
            --skip-build)
                SKIP_BUILD="true"
                ;;
            --dry-run)
                DRY_RUN="true"
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

build_documentation() {
    log "Building documentation..."
    "${SCRIPT_DIR}/build-documentation.sh"
}

validate_build() {
    if [[ ! -d "${BUILD_DIR}" ]]; then
        fail "Build directory not found: ${BUILD_DIR}. Run without --skip-build or build docs first."
    fi

    local file_count
    file_count=$(find "${BUILD_DIR}" -type f | wc -l)
    if [[ "${file_count}" -eq 0 ]]; then
        fail "Build directory is empty: ${BUILD_DIR}. The build may have failed."
    fi

    log "Build directory contains ${file_count} file(s)."
}

publish_documentation() {
    local rsync_args=(
        -avz
        --delete
        --checksum
    )

    if [[ "${DRY_RUN}" == "true" ]]; then
        rsync_args+=(--dry-run)
    fi

    local target="${SSH_HOST}:${DEPLOY_PATH}/"

    log "Publishing to ${target}"
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "(dry-run mode — no files will be transferred)"
    fi

    rsync "${rsync_args[@]}" "${BUILD_DIR}/" "${target}"
}

main() {
    parse_args "$@"
    check_dependencies

    if [[ "${SKIP_BUILD}" == "false" ]]; then
        build_documentation
    else
        log "Skipping build (--skip-build)"
    fi

    validate_build
    publish_documentation

    if [[ "${DRY_RUN}" == "true" ]]; then
        log "Dry-run completed. No files were transferred."
    else
        log "Documentation published to ${SSH_HOST}:${DEPLOY_PATH}/"
    fi
}

main "$@"
