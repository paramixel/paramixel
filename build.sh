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
readonly DEFAULT_GRADLE_PLUGIN_DIR="gradle-plugin"

TARGET="all"
SKIP_STATIC_ANALYSIS="false"
GRADLE_PLUGIN_DIR="${DEFAULT_GRADLE_PLUGIN_DIR}"

usage() {
    cat <<'EOF'
Usage: ./build.sh [TARGET] [OPTIONS]

Build Paramixel from the project root.

Targets:
  all                    Build the Maven project, then build the Gradle plugin.
                         This is the default target.
  maven                  Build and test the Maven project with the current Java.
  gradle-plugin          Sync the Gradle plugin version, install Maven
                         prerequisites locally, and build the Gradle plugin.
  sync-gradle-version    Sync the Gradle plugin version from the parent POM
                         without building.

Options:
  --skip-static-analysis       Skip SpotBugs and PMD during the Maven build.
                               Intended for Java 25 compatibility builds.
  --gradle-plugin-dir <dir>    Gradle plugin directory. Default: gradle-plugin
  -h, --help                   Show this help text.

Prerequisites:
  - Maven targets use the current JAVA_HOME/java on PATH.
  - gradle-plugin and all require JAVA_17_HOME to point to a JDK 17 directory.

Examples:
  ./build.sh
  ./build.sh maven
  ./build.sh maven --skip-static-analysis
  JAVA_17_HOME=/path/to/jdk17 ./build.sh gradle-plugin
  ./build.sh sync-gradle-version
EOF
}

log() {
    echo "[INFO] $*"
}

fail() {
    echo "[ERROR] $*" >&2
    exit 1
}

parse_args() {
    if [[ $# -gt 0 && "${1}" != -* ]]; then
        TARGET="$1"
        shift
    fi

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-static-analysis)
                SKIP_STATIC_ANALYSIS="true"
                ;;
            --gradle-plugin-dir)
                [[ -n "${2:-}" && "${2:-}" != --* ]] || fail "--gradle-plugin-dir requires a value"
                GRADLE_PLUGIN_DIR="$2"
                shift
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

    case "${TARGET}" in
        all|maven|gradle-plugin|sync-gradle-version)
            ;;
        *)
            usage >&2
            fail "Unknown target: ${TARGET}"
            ;;
    esac
}

require_project_root() {
    [[ -x "${SCRIPT_DIR}/mvnw" ]] || fail "mvnw not found or not executable: ${SCRIPT_DIR}/mvnw"
}

gradle_plugin_path() {
    if [[ "${GRADLE_PLUGIN_DIR}" = /* ]]; then
        echo "${GRADLE_PLUGIN_DIR}"
    else
        echo "${SCRIPT_DIR}/${GRADLE_PLUGIN_DIR}"
    fi
}

require_gradle_plugin_dir() {
    local plugin_dir
    plugin_dir="$(gradle_plugin_path)"

    [[ -d "${plugin_dir}" ]] || fail "Gradle plugin directory does not exist: ${plugin_dir}"
    [[ -x "${plugin_dir}/gradlew" ]] || fail "gradlew not executable: ${plugin_dir}/gradlew"
}

require_java_17_home() {
    if [[ -z "${JAVA_17_HOME:-}" ]]; then
        fail "JAVA_17_HOME is not set. Set JAVA_17_HOME to a JDK 17 installation directory."
    fi

    if [[ ! -d "${JAVA_17_HOME}" ]]; then
        fail "JAVA_17_HOME directory does not exist: ${JAVA_17_HOME}"
    fi

    if [[ ! -x "${JAVA_17_HOME}/bin/java" ]]; then
        fail "JAVA_17_HOME/bin/java not executable: ${JAVA_17_HOME}/bin/java"
    fi
}

run_maven_current_java() {
    (cd "${SCRIPT_DIR}" && ./mvnw -B "$@")
}

run_maven_java_17() {
    (
        cd "${SCRIPT_DIR}"
        JAVA_HOME="${JAVA_17_HOME}" \
            PATH="${JAVA_17_HOME}/bin:${PATH}" \
            ./mvnw -B "$@"
    )
}

run_gradle_java_17() {
    (
        cd "$(gradle_plugin_path)"
        JAVA_HOME="${JAVA_17_HOME}" \
            PATH="${JAVA_17_HOME}/bin:${PATH}" \
            ./gradlew "$@"
    )
}

get_project_version() {
    (cd "${SCRIPT_DIR}" && ./mvnw help:evaluate \
        -Dexpression=project.version \
        -q \
        -DforceStdout)
}

sync_gradle_version() {
    local version
    local gradle_properties
    gradle_properties="$(gradle_plugin_path)/gradle.properties"

    version="$(get_project_version)" || fail "Failed to extract version from parent POM"

    log "Syncing version ${version} to ${gradle_properties}"

    cat > "${gradle_properties}" <<EOF
version=${version}
group=org.paramixel
EOF
}

build_maven() {
    local args=(clean verify)

    if [[ "${SKIP_STATIC_ANALYSIS}" == "true" ]]; then
        args+=(-Dspotbugs.skip=true -Dpmd.skip=true)
    fi

    log "Building Maven project with current Java"
    run_maven_current_java "${args[@]}"
}

install_gradle_plugin_prerequisites() {
    log "Installing Maven prerequisites for Gradle plugin with Java 17"
    run_maven_java_17 clean install -N -DskipTests
    run_maven_java_17 clean install -pl core -DskipTests
}

build_gradle_plugin() {
    require_gradle_plugin_dir
    require_java_17_home

    sync_gradle_version
    install_gradle_plugin_prerequisites

    log "Building Gradle plugin with Java 17"
    run_gradle_java_17 clean build --no-daemon
}

main() {
    parse_args "$@"
    require_project_root

    case "${TARGET}" in
        all)
            require_gradle_plugin_dir
            require_java_17_home
            build_maven
            build_gradle_plugin
            ;;
        maven)
            build_maven
            ;;
        gradle-plugin)
            build_gradle_plugin
            ;;
        sync-gradle-version)
            require_gradle_plugin_dir
            sync_gradle_version
            ;;
    esac
}

main "$@"
