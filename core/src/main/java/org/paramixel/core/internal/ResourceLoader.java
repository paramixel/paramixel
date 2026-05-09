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

package org.paramixel.core.internal;

import java.io.InputStream;

/**
 * Loads classpath resources using a three-level ClassLoader fallback strategy.
 *
 * <p>The resolution order is:
 * <ol>
 *   <li>Thread context ClassLoader (if non-null)</li>
 *   <li>Defining ClassLoader (the ClassLoader that loaded {@code ResourceLoader})</li>
 *   <li>System ClassLoader</li>
 * </ol>
 *
 * <p>This utility centralizes resource-loading logic that was previously duplicated across
 * {@code Version} and {@code Configuration}, preventing maintenance drift.
 */
public final class ResourceLoader {

    private ResourceLoader() {}

    /**
     * Finds a classpath resource by name, trying the thread context ClassLoader, the defining
     * ClassLoader, and the system ClassLoader in order.
     *
     * @param name the resource name
     * @return an {@link InputStream} for the resource, or {@code null} if not found by any
     *     ClassLoader
     */
    public static InputStream getResourceAsStream(String name) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream stream = contextClassLoader.getResourceAsStream(name);
            if (stream != null) {
                return stream;
            }
        }
        return getResourceAsStreamWithoutContext(name);
    }

    /**
     * Finds a classpath resource by name, trying the provided ClassLoader first, then the defining
     * ClassLoader and system ClassLoader.
     *
     * @param name the resource name
     * @param classLoader the preferred ClassLoader, or {@code null} to use the defining/system
     *     fallback strategy
     * @return an {@link InputStream} for the resource, or {@code null} if not found by any
     *     ClassLoader
     */
    public static InputStream getResourceAsStream(String name, ClassLoader classLoader) {
        if (classLoader != null) {
            InputStream stream = classLoader.getResourceAsStream(name);
            if (stream != null) {
                return stream;
            }
        }
        return getResourceAsStreamWithoutContext(name);
    }

    private static InputStream getResourceAsStreamWithoutContext(String name) {
        ClassLoader definingClassLoader = ResourceLoader.class.getClassLoader();
        if (definingClassLoader != null) {
            return definingClassLoader.getResourceAsStream(name);
        }
        return ClassLoader.getSystemResourceAsStream(name);
    }
}
