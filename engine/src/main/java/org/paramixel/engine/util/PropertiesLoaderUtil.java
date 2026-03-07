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

package org.paramixel.engine.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.NonNull;

/**
 * Loads and normalizes {@code paramixel.properties}.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class PropertiesLoaderUtil {

    /**
     * Configuration key for the tag include filter.
     */
    private static final String TAGS_INCLUDE_KEY = "paramixel.tags.include";

    /**
     * Configuration key for the tag exclude filter.
     */
    private static final String TAGS_EXCLUDE_KEY = "paramixel.tags.exclude";

    /**
     * Internal invoker key that must not be provided via {@code paramixel.properties}.
     */
    private static final String INTERNAL_INVOKER_KEY = "paramixel.internal.invoker";

    /**
     * Creates a new utility instance.
     */
    private PropertiesLoaderUtil() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Loads {@code paramixel.properties} from the current working directory.
     *
     * <p>If the file is not present, this method returns empty properties.
     *
     * @param fileName the file name to load
     * @param keyForErrors the key used in error messages
     * @return the loaded properties, possibly empty
     */
    public static Properties loadProjectRootPropertiesOrFail(
            final @NonNull String fileName, final @NonNull String keyForErrors) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(keyForErrors, "keyForErrors must not be null");

        final Properties properties = new Properties();
        final File propertiesFile = new File(fileName);
        if (!propertiesFile.isFile()) {
            return properties;
        }

        try (InputStream in = new FileInputStream(propertiesFile)) {
            properties.load(in);
        } catch (Exception e) {
            throw new ConfigurationException(
                    keyForErrors,
                    "failed to load properties file",
                    EngineConfigurationUtil.Source.PROPERTIES_IO.id(),
                    fileName,
                    fileName,
                    e);
        }

        return properties;
    }

    /**
     * Normalizes all values in the provided properties.
     *
     * <p>Normalization is trim-then-unescape for all keys except that blank values are preserved
     * as empty strings.
     *
     * @param input the input properties
     * @return a new {@link Properties} instance containing normalized values
     */
    public static Properties normalizeAllValues(final @NonNull Properties input) {
        Objects.requireNonNull(input, "input must not be null");

        final Properties normalized = new Properties();
        for (Map.Entry<Object, Object> entry : input.entrySet()) {
            final Object keyObj = entry.getKey();
            final Object valueObj = entry.getValue();

            if (!(keyObj instanceof String)) {
                continue;
            }

            final String key = (String) keyObj;
            if (INTERNAL_INVOKER_KEY.equals(key)) {
                throw new ConfigurationException(
                        key,
                        "must not be provided via paramixel.properties",
                        EngineConfigurationUtil.Source.PROPERTIES_FILE.id(),
                        String.valueOf(valueObj == null ? "" : valueObj),
                        "");
            }

            final String rawValue = valueObj == null ? "" : String.valueOf(valueObj);

            if (TAGS_INCLUDE_KEY.equals(key) || TAGS_EXCLUDE_KEY.equals(key)) {
                normalized.setProperty(
                        key,
                        EngineConfigurationUtil.normalizeProvided(
                                key, rawValue, EngineConfigurationUtil.Source.PROPERTIES_FILE));
                continue;
            }

            final String trimmed = rawValue.trim();
            if (trimmed.isEmpty()) {
                normalized.setProperty(key, "");
                continue;
            }

            normalized.setProperty(
                    key,
                    EngineConfigurationUtil.normalizeProvided(
                            key, rawValue, EngineConfigurationUtil.Source.PROPERTIES_FILE));
        }

        return normalized;
    }
}
