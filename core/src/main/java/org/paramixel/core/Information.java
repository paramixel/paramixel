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

public class Information {

    private static final String RESOURCE_NAME = "information.properties";

    private static final String VERSION_PROPERTY = "version";

    private static final String VERSION = loadVersion();

    private Information() {}

    public static String getVersion() {
        return VERSION;
    }

    private static String loadVersion() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(RESOURCE_NAME);
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
}
