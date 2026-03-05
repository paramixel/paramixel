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

package org.paramixel.engine.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.NonNull;

/**
 * Factory for creating {@link TagFilter} instances from various configuration sources.
 *
 * <p>This factory supports creating filters from:
 * <ul>
 *   <li>System properties</li>
 *   <li>Configuration parameters (JUnit Platform)</li>
 *   <li>Properties files</li>
 *   <li>Programmatic list of patterns</li>
 * </ul>
 *
 * @since 0.0.1
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class TagFilterFactory {

    /**
     * Stores the TAGS_INCLUDE_KEY.
     */
    private static final String TAGS_INCLUDE_KEY = "paramixel.tags.include";
    /**
     * Stores the TAGS_EXCLUDE_KEY.
     */
    private static final String TAGS_EXCLUDE_KEY = "paramixel.tags.exclude";

    /**
     * Creates a new instance.
     *
     * @since 0.0.1
     */
    private TagFilterFactory() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Creates a tag filter from system properties.
     *
     * <p>Reads {@code paramixel.tags.include} and {@code paramixel.tags.exclude} from
     * system properties. Patterns are comma-separated.</p>
     *
     * @return a configured {@link TagFilter}; never {@code null}
     * @since 0.0.1
     */
    public static TagFilter fromSystemProperties() {
        return fromProperties(System.getProperties());
    }

    /**
     * Creates a tag filter from a properties object.
     *
     * <p>Reads {@code paramixel.tags.include} and {@code paramixel.tags.exclude} from
     * the provided properties. Patterns are comma-separated.</p>
     *
     * @param properties the properties to read from; never {@code null}
     * @return a configured {@link TagFilter}; never {@code null}
     * @since 0.0.1
     */
    public static TagFilter fromProperties(final @NonNull Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");

        final String includeValue = properties.getProperty(TAGS_INCLUDE_KEY, "");
        final String excludeValue = properties.getProperty(TAGS_EXCLUDE_KEY, "");

        return fromPatterns(parsePatterns(includeValue), parsePatterns(excludeValue));
    }

    /**
     * Performs fromConfigurationParameters.
     *
     * @param configParameters the configParameters
     * @return the result
     * @since 0.0.1
     */
    public static TagFilter fromConfigurationParameters(
            final org.junit.platform.engine.ConfigurationParameters configParameters) {
        Objects.requireNonNull(configParameters, "configParameters must not be null");

        final String includeValue = configParameters.get(TAGS_INCLUDE_KEY).orElse("");
        final String excludeValue = configParameters.get(TAGS_EXCLUDE_KEY).orElse("");

        return fromPatterns(parsePatterns(includeValue), parsePatterns(excludeValue));
    }

    /**
     * Creates a tag filter from explicit pattern strings.
     *
     * @param includePatterns comma-separated include patterns; may be empty or {@code null}
     * @param excludePatterns comma-separated exclude patterns; may be empty or {@code null}
     * @return a configured {@link TagFilter}; never {@code null}
     * @since 0.0.1
     */
    public static TagFilter fromPatternStrings(final String includePatterns, final String excludePatterns) {
        return fromPatterns(parsePatterns(includePatterns), parsePatterns(excludePatterns));
    }

    /**
     * Performs fromPatterns.
     *
     * @param includePatterns the includePatterns
     * @param excludePatterns the excludePatterns
     * @return the result
     * @since 0.0.1
     */
    public static TagFilter fromPatterns(
            final @NonNull List<String> includePatterns, final @NonNull List<String> excludePatterns) {
        Objects.requireNonNull(includePatterns, "includePatterns must not be null");
        Objects.requireNonNull(excludePatterns, "excludePatterns must not be null");

        return new RegexTagFilter(includePatterns, excludePatterns);
    }

    /**
     * Parses a comma-separated string of patterns into a list.
     *
     * <p>Empty strings and whitespace-only patterns are filtered out.</p>
     *
     * @param value the comma-separated pattern string; may be {@code null} or empty
     * @return list of non-empty patterns; never {@code null}
     * @since 0.0.1
     */
    private static List<String> parsePatterns(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> patterns = new ArrayList<>();
        final String[] parts = value.split(",");

        for (String part : parts) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(trimmed);
            }
        }

        return Collections.unmodifiableList(patterns);
    }
}
