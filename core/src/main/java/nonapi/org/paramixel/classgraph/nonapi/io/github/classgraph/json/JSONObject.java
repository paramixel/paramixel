/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** An intermediate object in the (de)serialization process, representing a JSON Object. */
class JSONObject {
    /** Key/value mappings, in display order. */
    List<Entry<String, Object>> items;

    /** Object id for cross-references, if known. */
    CharSequence objectId;

    /**
     * Constructor.
     *
     * @param sizeHint
     *            the size hint
     */
    public JSONObject(final int sizeHint) {
        items = new ArrayList<>(sizeHint);
    }

    /**
     * Constructor.
     *
     * @param items
     *            the items
     */
    public JSONObject(final List<Entry<String, Object>> items) {
        this.items = items;
    }

    /**
     * Serialize this JSONObject to a string.
     *
     * @param jsonReferenceToId
     *            a map from json reference to id
     * @param includeNullValuedFields
     *            if true, include null valued fields
     * @param depth
     *            the nesting depth
     * @param indentWidth
     *            the indent width
     * @param buf
     *            the buf
     */
    void toJSONString(
            final Map<ReferenceEqualityKey<JSONReference>, CharSequence> jsonReferenceToId,
            final boolean includeNullValuedFields,
            final int depth,
            final int indentWidth,
            final StringBuilder buf) {
        final boolean prettyPrint = indentWidth > 0;
        final int n = items.size();
        int numDisplayedFields;
        if (includeNullValuedFields) {
            numDisplayedFields = n;
        } else {
            numDisplayedFields = 0;
            for (final Entry<String, Object> item : items) {
                if (item.getValue() != null) {
                    numDisplayedFields++;
                }
            }
        }
        if (objectId == null && numDisplayedFields == 0) {
            buf.append("{}");
        } else {
            buf.append(prettyPrint ? "{\n" : "{");
            if (objectId != null) {
                // id will be non-null if this object does not have an @Id field, but was referenced by
                // another object (need to include ID_TAG)
                if (prettyPrint) {
                    JSONUtils.indent(depth + 1, indentWidth, buf);
                }
                buf.append('"');
                buf.append(JSONUtils.ID_KEY);
                buf.append(prettyPrint ? "\": " : "\":");
                JSONSerializer.jsonValToJSONString(
                        objectId, jsonReferenceToId, includeNullValuedFields, depth + 1, indentWidth, buf);
                if (numDisplayedFields > 0) {
                    buf.append(',');
                }
                if (prettyPrint) {
                    buf.append('\n');
                }
            }
            for (int i = 0, j = 0; i < n; i++) {
                final Entry<String, Object> item = items.get(i);
                final Object val = item.getValue();
                if (val != null || includeNullValuedFields) {
                    final String key = item.getKey();
                    if (key == null) {
                        // Keys must be quoted, so the unquoted null value cannot be a key
                        // (Should not happen -- JSONParser.parseJSONObject checks for null keys)
                        throw new IllegalArgumentException("Cannot serialize JSON object with null key");
                    }
                    if (prettyPrint) {
                        JSONUtils.indent(depth + 1, indentWidth, buf);
                    }
                    buf.append('"');
                    JSONUtils.escapeJSONString(key, buf);
                    buf.append(prettyPrint ? "\": " : "\":");
                    JSONSerializer.jsonValToJSONString(
                            val, jsonReferenceToId, includeNullValuedFields, depth + 1, indentWidth, buf);
                    if (++j < numDisplayedFields) {
                        buf.append(',');
                    }
                    if (prettyPrint) {
                        buf.append('\n');
                    }
                }
            }
            if (prettyPrint) {
                JSONUtils.indent(depth, indentWidth, buf);
            }
            buf.append('}');
        }
    }
}
