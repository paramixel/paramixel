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

package examples.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads classpath resource files as lists of non-blank, non-comment lines.
 *
 * <p>Blank lines and lines starting with {@code #} are silently excluded.
 */
public class Resource {

    private Resource() {
        // Intentionally empty
    }

    /**
     * Reads a classpath resource relative to the package of {@code clazz} and returns
     * its non-blank, non-comment lines.
     *
     * <p>If {@code relativeResourceName} starts with {@code /}, the path is appended to
     * the package of {@code clazz}; otherwise a {@code /} separator is inserted.
     *
     * @param clazz class whose package anchors the resource path
     * @param relativeResourceName resource path, optionally starting with {@code /}
     * @return unmodifiable list of trimmed, non-blank lines that do not start with {@code #}
     * @throws IOException if the resource cannot be found on the classpath
     */
    public static List<String> load(final Class<?> clazz, final String relativeResourceName) throws IOException {
        var packagePath = clazz.getPackage().getName().replace('.', '/');

        try (var in = getInputStream(relativeResourceName, packagePath);
                var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            return reader.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .toList();
        }
    }

    private static InputStream getInputStream(final String relativeResourceName, final String packageName)
            throws IOException {
        String qualifiedResourceName;
        if (relativeResourceName.startsWith("/")) {
            qualifiedResourceName = packageName + relativeResourceName;
        } else {
            qualifiedResourceName = packageName + "/" + relativeResourceName;
        }

        var classLoader = Thread.currentThread().getContextClassLoader();
        var inputStream = classLoader.getResourceAsStream(qualifiedResourceName);
        if (inputStream == null) {
            throw new IOException("Classpath resource not found: " + qualifiedResourceName);
        }
        return inputStream;
    }
}
