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

readonly WEBSITE_DIR="website"
readonly DOCS_DIR="${WEBSITE_DIR}/docs"
readonly SIDEBARS_FILE="${WEBSITE_DIR}/sidebars.js"
readonly SIDEBAR_JS_DOC_ID_PATTERN="^['\"][a-zA-Z0-9_/-]+['\"]\$"

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
    if ! command -v node &>/dev/null; then
        fail "node is not installed. Please install Node.js 18+."
    fi

    if ! command -v npm &>/dev/null; then
        fail "npm is not installed. Please install Node.js 18+."
    fi

    if ! command -v jq &>/dev/null; then
        fail "jq is not installed. Please install jq."
    fi

    if ! command -v gawk &>/dev/null; then
        fail "gawk is not installed. Please install gawk."
    fi
}

resolve_doc_id() {
    local file="$1"
    local frontmatter_id
    frontmatter_id=$(gawk '
        /^---$/ { in_fm = (in_fm ? 0 : 1); next }
        in_fm && /^id:/ {
            match($0, /^id:[[:space:]]*(.+)$/, arr)
            if (RSTART > 0) print arr[1]
            exit
        }
    ' "$file" 2>/dev/null)

    if [[ -n "${frontmatter_id:-}" ]]; then
        echo "$frontmatter_id"
        return
    fi

    local relative_path
    relative_path="${file#${DOCS_DIR}/}"
    relative_path="${relative_path%.md}"
    relative_path="${relative_path%.mdx}"
    echo "$relative_path"
}

get_all_doc_ids() {
    local file
    while IFS= read -r -d '' file; do
        resolve_doc_id "$file"
    done < <(find "$DOCS_DIR" -type f \( -name "*.md" -o -name "*.mdx" \) -print0) | sort -u
}

get_sidebar_doc_ids() {
    if [[ ! -f "$SIDEBARS_FILE" ]]; then
        fail "Sidebar file not found: $SIDEBARS_FILE"
    fi

    local temp_file
    temp_file=$(mktemp)
    trap "rm -f '$temp_file'" RETURN

    node -e "
        const sidebars = require('./${SIDEBARS_FILE}');
        const ids = [];
        
        function extractIds(items) {
            for (const item of items) {
                if (typeof item === 'string') {
                    ids.push(item);
                } else if (item && typeof item === 'object') {
                    if (item.items) {
                        extractIds(item.items);
                    }
                }
            }
        }
        
        for (const sidebar of Object.values(sidebars)) {
            extractIds(sidebar);
        }
        
        console.log(ids.join('\\n'));
    " > "$temp_file" 2>/dev/null || fail "Failed to parse sidebars.js"

    sort -u < "$temp_file"
}

validate_sidebar_ids() {
    log "Validating sidebar document references..."

    local sidebar_ids actual_ids missing_ids
    sidebar_ids=$(get_sidebar_doc_ids)
    actual_ids=$(get_all_doc_ids)

    missing_ids=""
    local id
    while IFS= read -r id; do
        [[ -z "$id" ]] && continue
        if ! echo "$actual_ids" | grep -qx "$id"; then
            if [[ -n "$missing_ids" ]]; then
                missing_ids="${missing_ids}"$'\n'"  - $id"
            else
                missing_ids="  - $id"
            fi
        fi
    done <<< "$sidebar_ids"

    if [[ -n "$missing_ids" ]]; then
        fail "Sidebar references non-existent document IDs:${missing_ids}"$'\n'"Available document IDs:"$'\n'"$(echo "$actual_ids" | sed 's/^/  - /')"
    fi

    log "Sidebar document references validated."
}

validate_frontmatter() {
    log "Validating frontmatter..."

    local file missing_title missing_desc has_custom_id default_id actual_id relative_path issues=0

    while IFS= read -r -d '' file; do
        missing_title=""
        missing_desc=""
        has_custom_id=""

        relative_path="${file#${DOCS_DIR}/}"

        if ! gawk '/^---$/{fm=(fm?0:1);next}fm&&/^title:/{t=1}fm&&/^description:/{d=1}END{exit(t&&d?0:1)}' "$file" 2>/dev/null; then
            if ! gawk 'BEGIN{fm=0} /^---$/{fm=(fm?0:1);next} fm && /^title:/ {t=1} fm && /^description:/ {d=1} END {exit (t && d ? 0 : 1)}' "$file" 2>/dev/null; then
                warn "$relative_path: missing 'title' or 'description' in frontmatter"
                ((issues++)) || true
            fi
        fi

        has_custom_id=$(gawk '
            /^---$/ { in_fm = (in_fm ? 0 : 1); next }
            in_fm && /^id:/ { print "yes"; exit }
        ' "$file" 2>/dev/null)

        if [[ "$has_custom_id" == "yes" ]]; then
            actual_id=$(resolve_doc_id "$file")
            default_id="${relative_path%.md}"
            default_id="${default_id%.mdx}"
            if [[ "$actual_id" != "$default_id" ]]; then
                warn "$relative_path: custom id '$actual_id' differs from path-based default '$default_id' (may cause confusion)"
            fi
        fi
    done < <(find "$DOCS_DIR" -type f \( -name "*.md" -o -name "*.mdx" \) -print0)

    if [[ $issues -gt 0 ]]; then
        fail "Frontmatter validation failed with $issues issue(s)"
    fi

    log "Frontmatter validated."
}

validate_internal_links() {
    log "Validating internal links..."

    local file link target issues=0 broken_links=""

    while IFS= read -r -d '' file; do
        local relative_path="${file#${DOCS_DIR}/}"

        while IFS= read -r link; do
            [[ -z "$link" ]] && continue

            if [[ "$link" =~ ^/docs/ ]]; then
                target="${DOCS_DIR}/${link#/docs/}"
            elif [[ "$link" =~ ^\.\./ ]]; then
                target="$(dirname "$file")/${link}"
            elif [[ "$link" =~ ^\./ ]]; then
                target="$(dirname "$file")/${link#./}"
            else
                continue
            fi

            target=$(cd "$(dirname "$target")" 2>/dev/null && realpath -m "$(basename "$target")" 2>/dev/null) || continue

            if [[ "$link" =~ \.md$|\.mdx$ ]]; then
                if [[ ! -f "$target" ]]; then
                    broken_links="${broken_links}"$'\n'"  - $relative_path: $link"
                    ((issues++)) || true
                fi
            fi
        done < <(grep -oE '\]\([^)]+\)' "$file" 2>/dev/null | sed 's/](\(.*\))/\1/' | grep -E '^/docs/|^\.\./|^\./' || true)
    done < <(find "$DOCS_DIR" -type f \( -name "*.md" -o -name "*.mdx" \) -print0)

    if [[ -n "$broken_links" ]]; then
        fail "Broken internal links found:${broken_links}"
    fi

    log "Internal links validated."
}

install_dependencies() {
    log "Installing dependencies in ${WEBSITE_DIR}/..."

    if [[ -f "${WEBSITE_DIR}/package-lock.json" ]]; then
        npm ci --prefix "${WEBSITE_DIR}"
    else
        npm install --prefix "${WEBSITE_DIR}"
    fi
}

build_documentation() {
    log "Building documentation..."
    npm run build --prefix "${WEBSITE_DIR}"
}

main() {
    log "Checking documentation build..."

    check_dependencies

    validate_sidebar_ids

    validate_frontmatter

    validate_internal_links

    install_dependencies

    build_documentation

    log "Documentation build completed successfully."
}

main "$@"
