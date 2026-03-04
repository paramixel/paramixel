/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/**
 * Utility for loading classpath resources.
 */
public final class Resource {

    /**
     * Prevents instantiation of utility class.
     */
    private Resource() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Loads a classpath resource as a list of trimmed, non-empty lines.
     *
     * @param clazz the class whose package is used as the base path
     * @param relativeResourceName the resource name relative to the package
     * @return the list of resource lines
     * @throws IOException when the resource is missing or cannot be read
     */
    public static List<String> load(final @NonNull Class<?> clazz, final @NonNull String relativeResourceName)
            throws IOException {
        String packageName = clazz.getPackage().getName().replace('.', '/');

        String qualifiedResourceName;
        if (relativeResourceName.startsWith("/")) {
            qualifiedResourceName = packageName + relativeResourceName;
        } else {
            qualifiedResourceName = packageName + "/" + relativeResourceName;
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(qualifiedResourceName);
        if (inputStream == null) {
            throw new IOException("Classpath resource not found: " + qualifiedResourceName);
        }

        try (BufferedReader bufferedReader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return bufferedReader
                    .lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !s.startsWith("#"))
                    .collect(Collectors.toList());
        }
    }
}
