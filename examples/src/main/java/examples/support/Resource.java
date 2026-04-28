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

public class Resource {

    private Resource() {}

    public static List<String> load(Class<?> clazz, String relativeResourceName) throws IOException {
        String packagePath = clazz.getPackage().getName().replace('.', '/');

        try (InputStream in = getInputStream(relativeResourceName, packagePath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            return reader.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .toList();
        }
    }

    private static InputStream getInputStream(String relativeResourceName, String packageName) throws IOException {
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
        return inputStream;
    }
}
