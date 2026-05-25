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

package org.paramixel.api.internal.listener.support;

import java.io.IOException;
import java.io.StringWriter;

@FunctionalInterface
interface JsonBuilderTestSupport {

    void accept(final JsonBuilder builder) throws IOException;

    static String buildJson(final JsonBuilderTestSupport consumer) throws IOException {
        StringWriter sw = new StringWriter();
        JsonBuilder builder = new JsonBuilder(sw);
        consumer.accept(builder);
        return sw.toString();
    }

    static String buildHtmlScriptJson(final JsonBuilderTestSupport consumer) throws IOException {
        StringWriter sw = new StringWriter();
        JsonBuilder builder = new JsonBuilder(sw, true);
        consumer.accept(builder);
        return sw.toString();
    }
}
