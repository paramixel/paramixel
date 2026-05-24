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
readonly DOCS_DIR="${PROJECT_DIR}/website/docs"
readonly VERSIONED_DOCS_DIR="${PROJECT_DIR}/website/versioned_docs"
readonly SIDEBAR_JS="${PROJECT_DIR}/website/sidebars.js"
readonly VERSIONED_SIDEBARS_DIR="${PROJECT_DIR}/website/versioned_sidebars"

usage() {
    cat <<'EOF'
Usage: ./scripts/verify-versioned-docs.sh [version]

Verify that unreleased docs (website/docs/) and versioned docs match,
allowing only version-specific string differences.

Arguments:
  version    Version to verify (default: checks all versions)

Examples:
  ./scripts/verify-versioned-docs.sh 5.0.0
  ./scripts/verify-versioned-docs.sh
EOF
}

log() {
    echo "[INFO] $*"
}

fail() {
    echo "[ERROR] $*" >&2
    exit 1
}

verify_docs() {
    local version="$1"
    local versioned_dir="${VERSIONED_DOCS_DIR}/version-${version}"

    if [[ ! -d "${versioned_dir}" ]]; then
        fail "Versioned docs directory not found: ${versioned_dir}"
    fi

    log "Verifying docs/ matches version-${version}/"

    local diff_output
    diff_output="$(diff -rq "${DOCS_DIR}/" "${versioned_dir}/" 2>&1 || true)"

    if [[ -z "${diff_output}" ]]; then
        log "  PASS: All files identical"
        return 0
    fi

    local drift_count
    drift_count="$(echo "${diff_output}" | grep -c "^Files" || true)"
    local only_in_docs
    only_in_docs="$(echo "${diff_output}" | grep "Only in ${DOCS_DIR}" || true)"
    local only_in_version
    only_in_version="$(echo "${diff_output}" | grep "Only in ${versioned_dir}" || true)"

    if [[ -n "${only_in_docs}" ]]; then
        log "  Files only in docs/ (unreleased):"
        echo "${only_in_docs}" | sed 's/^/    /'
    fi

    if [[ -n "${only_in_version}" ]]; then
        log "  Files only in version-${version}/:"
        echo "${only_in_version}" | sed 's/^/    /'
    fi

    if [[ "${drift_count}" -gt 0 ]]; then
        log "  ${drift_count} file(s) with content differences:"
        echo "${diff_output}" | grep "^Files" | sed 's/^/    /'
        log ""
        log "  Detailed diff:"
        local differing_files
        differing_files="$(echo "${diff_output}" | grep "^Files" | sed 's/ and .*//')"
        for file in ${differing_files}; do
            local rel
            rel="${file#${DOCS_DIR}/}"
            diff "${DOCS_DIR}/${rel}" "${versioned_dir}/${rel}" || true
            echo "---"
        done
    fi

    return 1
}

verify_sidebar() {
    local version="$1"
    local versioned_sidebar="${VERSIONED_SIDEBARS_DIR}/version-${version}-sidebars.json"

    if [[ ! -f "${versioned_sidebar}" ]]; then
        log "  WARN: No sidebar found for version ${version}: ${versioned_sidebar}"
        return 0
    fi

    log "Verifying sidebar structure matches for version ${version}"
    log "  (Manual review required — sidebars.js vs version-${version}-sidebars.json)"
    return 0
}

main() {
    local versions

    if [[ $# -eq 0 ]]; then
        if [[ ! -f "${PROJECT_DIR}/website/versions.json" ]]; then
            fail "versions.json not found at ${PROJECT_DIR}/website/versions.json"
        fi
        versions="$(cat "${PROJECT_DIR}/website/versions.json" | tr -d '[]" ' | tr ',' ' ')"
    elif [[ $# -eq 1 ]]; then
        versions="$1"
    else
        usage
        exit 1
    fi

    if [[ ! -d "${DOCS_DIR}" ]]; then
        fail "Unreleased docs directory not found: ${DOCS_DIR}"
    fi

    local exit_code=0

    for version in ${versions}; do
        log ""
        log "=== Version ${version} ==="
        if verify_docs "${version}"; then
            :
        else
            exit_code=1
        fi
        verify_sidebar "${version}"
    done

    log ""
    if [[ "${exit_code}" -eq 0 ]]; then
        log "All versions verified successfully"
    else
        log "Drift detected — see details above"
    fi

    exit "${exit_code}"
}

main "$@"
