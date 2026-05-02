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

package org.paramixel.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Exposes build and runtime metadata for the Paramixel core library.
 *
 * <p>This utility currently provides access to the Paramixel version declared in the
 * classpath resource {@code information.properties}. The value is resolved once when
 * the class is initialized and then cached for the lifetime of the class loader.</p>
 *
 * <p>The class is intended for informational use such as diagnostic logging, banners,
 * or tooling that needs to report the active Paramixel version at runtime.</p>
 *
 * <h3>Class-loader resolution</h3>
 * <p>Resources are loaded using the thread context class loader first. If the context
 * class loader is unavailable ({@code null}) or cannot find the resource, the defining
 * class loader ({@code Information.class.getClassLoader()}) is tried as a fallback.
 * This ensures reliable resource loading in containers, plugins, and test runners that
 * set a restricted or different context class loader.</p>
 */
public final class Information {

    private static final String RESOURCE_NAME = "information.properties";

    private static final String VERSION_PROPERTY = "version";

    private static final String VERSION = loadVersion();

    private Information() {}

    /**
     * Returns the Paramixel version loaded from the bundled metadata resource.
     *
     * <p>The returned value is cached during class initialization, so repeated calls do
     * not re-read the classpath resource.</p>
     *
     * <p>The version is resolved using the thread context class loader first. If the
     * context class loader is unavailable or cannot locate the resource, the defining
     * class loader is used as a fallback.</p>
     *
     * @return the Paramixel version string; never {@code null} or blank
     * @throws IllegalStateException if the version metadata resource is missing or invalid
     */
    public static String getVersion() {
        return VERSION;
    }

    private static String loadVersion() {
        InputStream inputStream = getResourceAsStream(RESOURCE_NAME);
        if (inputStream == null) {
            throw new IllegalStateException("missing classpath resource: " + RESOURCE_NAME);
        }
        Properties properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load classpath resource: " + RESOURCE_NAME, e);
        }
        String version = properties.getProperty(VERSION_PROPERTY);
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("missing property: " + VERSION_PROPERTY);
        }
        return version;
    }

    static InputStream getResourceAsStream(String name) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream stream = contextClassLoader.getResourceAsStream(name);
            if (stream != null) {
                return stream;
            }
        }
        ClassLoader definingClassLoader = Information.class.getClassLoader();
        if (definingClassLoader != null) {
            return definingClassLoader.getResourceAsStream(name);
        }
        return ClassLoader.getSystemResourceAsStream(name);
    }
}
