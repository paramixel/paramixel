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
import org.paramixel.core.internal.ResourceLoader;

/**
 * Exposes the Paramixel version.
 *
 * <p>The current implementation loads version information from the classpath resource
 * {@code version.properties} when the class is initialized. If the resource is missing or
 * unreadable, the version falls back to {@link #UNKNOWN}.
 */
public final class Version {

    /**
     * Fallback version returned when the version resource is missing or unreadable.
     */
    public static final String UNKNOWN = "UNKNOWN";

    private static final String RESOURCE_NAME = "version.properties";

    private static final String VERSION_PROPERTY = "version";

    private static final String VERSION = loadVersion();

    private Version() {
        // Intentionally empty
    }

    /**
     * Returns the Paramixel version.
     *
     * @return the version string, or {@link #UNKNOWN} if the version resource is missing or unreadable
     */
    public static String getVersion() {
        return VERSION;
    }

    private static String loadVersion() {
        InputStream inputStream = ResourceLoader.getResourceAsStream(RESOURCE_NAME);
        if (inputStream == null) {
            return UNKNOWN;
        }
        var properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        } catch (IOException e) {
            return UNKNOWN;
        }
        String version = properties.getProperty(VERSION_PROPERTY);
        if (version == null || version.isBlank()) {
            return UNKNOWN;
        }
        return version;
    }
}
