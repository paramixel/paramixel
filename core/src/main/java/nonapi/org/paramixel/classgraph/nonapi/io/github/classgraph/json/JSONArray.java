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

/** An intermediate object in the (de)serialization process, representing a JSON array. */
class JSONArray {
    /** Array items. */
    List<Object> items;

    /**
     * Constructor.
     */
    public JSONArray() {
        items = new ArrayList<>();
    }

    /**
     * Constructor.
     *
     * @param items
     *            the items
     */
    public JSONArray(final List<Object> items) {
        this.items = items;
    }

    /**
     * Serialize this JSONArray to a string.
     *
     * @param jsonReferenceToId
     *            the map from json reference to id
     * @param includeNullValuedFields
     *            whether to include null-valued fields
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
        if (n == 0) {
            buf.append("[]");
        } else {
            buf.append('[');
            if (prettyPrint) {
                buf.append('\n');
            }
            for (int i = 0; i < n; i++) {
                final Object item = items.get(i);
                if (prettyPrint) {
                    JSONUtils.indent(depth + 1, indentWidth, buf);
                }
                JSONSerializer.jsonValToJSONString(
                        item, jsonReferenceToId, includeNullValuedFields, depth + 1, indentWidth, buf);
                if (i < n - 1) {
                    buf.append(',');
                }
                if (prettyPrint) {
                    buf.append('\n');
                }
            }
            if (prettyPrint) {
                JSONUtils.indent(depth, indentWidth, buf);
            }
            buf.append(']');
        }
    }
}
