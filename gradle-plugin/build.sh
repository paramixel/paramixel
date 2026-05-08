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

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly GRADLE_PROPERTIES="${SCRIPT_DIR}/gradle.properties"

usage() {
    cat <<'EOF'
Usage: ./build.sh [OPTIONS]

Build the Paramixel Gradle plugin.

Prerequisites:
  - JAVA_17_HOME must be set to a JDK 17 installation directory
  - org.paramixel:core must be available (run ./mvnw clean install at the
    project root first, or ensure core is published to Maven Central)

Options:
  --sync-version    Sync version from the parent POM to gradle.properties
                     and exit (no build). Intended for use by GitHub Actions
                     release workflow.
  -h, --help         Show this help text
EOF
}

log() {
    echo "[INFO] $*"
}

fail() {
    echo "[ERROR] $*" >&2
    exit 1
}

sync_version() {
    local version

    version="$(cd "${ROOT_DIR}" && ./mvnw help:evaluate \
        -Dexpression=project.version \
        -q \
        -DforceStdout)" || fail "Failed to extract version from parent POM"

    log "Syncing version ${version} to ${GRADLE_PROPERTIES}"

    cat > "${GRADLE_PROPERTIES}" <<EOF
version=${version}
group=org.paramixel
EOF
}

build_plugin() {
    if [[ -z "${JAVA_17_HOME:-}" ]]; then
        fail "JAVA_17_HOME is not set. Set JAVA_17_HOME to a JDK 17 installation directory."
    fi

    if [[ ! -d "${JAVA_17_HOME}" ]]; then
        fail "JAVA_17_HOME directory does not exist: ${JAVA_17_HOME}"
    fi

    (cd "${ROOT_DIR}" && ./mvnw clean install -pl core -DskipTests)
    JAVA_HOME="${JAVA_17_HOME}" ./gradlew clean build --no-daemon
}

main() {
    local sync_only=false

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --sync-version)
                sync_only=true
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

    sync_version

    if [[ "${sync_only}" == true ]]; then
        log "Version synced. Exiting (--sync-version mode)."
        exit 0
    fi

    build_plugin
}

main "$@"
