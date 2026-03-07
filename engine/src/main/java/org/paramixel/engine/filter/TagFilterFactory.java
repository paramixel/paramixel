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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.ConfigurationParameters;
import org.paramixel.engine.util.EngineConfigurationUtil;

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
     */
    private TagFilterFactory() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Creates a tag filter from system properties.
     *
     * <p>Reads {@code paramixel.tags.include} and {@code paramixel.tags.exclude} from
     * system properties. Each value represents a single regex pattern.</p>
     *
     * @return a configured {@link TagFilter}; never {@code null}
     */
    public static TagFilter fromSystemProperties() {
        return fromProperties(System.getProperties(), EngineConfigurationUtil.Source.SYSTEM_PROPERTIES);
    }

    /**
     * Creates a tag filter from a properties object.
     *
     * <p>Reads {@code paramixel.tags.include} and {@code paramixel.tags.exclude} from
     * the provided properties. Each value represents a single regex pattern.</p>
     *
     * @param properties the properties to read from; never {@code null}
     * @return a configured {@link TagFilter}; never {@code null}
     */
    public static TagFilter fromProperties(final @NonNull Properties properties) {
        return fromProperties(properties, EngineConfigurationUtil.Source.PROPERTIES_FILE);
    }

    public static TagFilter fromProperties(
            final @NonNull Properties properties, final EngineConfigurationUtil.Source source) {
        Objects.requireNonNull(properties, "properties must not be null");

        final boolean includePresent = properties.containsKey(TAGS_INCLUDE_KEY);
        final boolean excludePresent = properties.containsKey(TAGS_EXCLUDE_KEY);

        final String includeValue = includePresent ? properties.getProperty(TAGS_INCLUDE_KEY, "") : null;
        final String excludeValue = excludePresent ? properties.getProperty(TAGS_EXCLUDE_KEY, "") : null;

        return fromOptionalPatterns(includeValue, includePresent, excludeValue, excludePresent, source, source);
    }

    /**
     * Performs fromConfigurationParameters.
     *
     * @param configParameters the configParameters
     * @return the result
     */
    public static TagFilter fromConfigurationParameters(final @NonNull ConfigurationParameters configParameters) {
        Objects.requireNonNull(configParameters, "configParameters must not be null");

        return fromConfigurationParametersAndProperties(configParameters, new Properties());
    }

    public static TagFilter fromConfigurationParametersAndProperties(
            final @NonNull ConfigurationParameters configParameters, final @NonNull Properties properties) {
        Objects.requireNonNull(configParameters, "configParameters must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        final boolean includeFromConfig = configParameters.get(TAGS_INCLUDE_KEY).isPresent();
        final boolean excludeFromConfig = configParameters.get(TAGS_EXCLUDE_KEY).isPresent();

        final boolean includePresent = includeFromConfig || properties.containsKey(TAGS_INCLUDE_KEY);
        final boolean excludePresent = excludeFromConfig || properties.containsKey(TAGS_EXCLUDE_KEY);

        final String includeValue = includeFromConfig
                ? configParameters.get(TAGS_INCLUDE_KEY).orElse("")
                : properties.getProperty(TAGS_INCLUDE_KEY);
        final String excludeValue = excludeFromConfig
                ? configParameters.get(TAGS_EXCLUDE_KEY).orElse("")
                : properties.getProperty(TAGS_EXCLUDE_KEY);

        final EngineConfigurationUtil.Source includeSource = includeFromConfig
                ? EngineConfigurationUtil.Source.JUNIT_CONFIG
                : EngineConfigurationUtil.Source.PROPERTIES_FILE;
        final EngineConfigurationUtil.Source excludeSource = excludeFromConfig
                ? EngineConfigurationUtil.Source.JUNIT_CONFIG
                : EngineConfigurationUtil.Source.PROPERTIES_FILE;

        return fromOptionalPatterns(
                includeValue, includePresent, excludeValue, excludePresent, includeSource, excludeSource);
    }

    /**
     * Creates a tag filter from explicit pattern strings.
     *
     * @param includePattern include pattern; may be {@code null} to indicate no include filtering
     * @param excludePattern exclude pattern; may be {@code null} to indicate no exclude filtering
     * @return a configured {@link TagFilter}; never {@code null}
     */
    public static TagFilter fromPatternStrings(final String includePattern, final String excludePattern) {
        return fromOptionalPatterns(
                includePattern,
                includePattern != null,
                excludePattern,
                excludePattern != null,
                EngineConfigurationUtil.Source.PROGRAMMATIC,
                EngineConfigurationUtil.Source.PROGRAMMATIC);
    }

    private static TagFilter fromOptionalPatterns(
            final String includeValue,
            final boolean includePresent,
            final String excludeValue,
            final boolean excludePresent,
            final EngineConfigurationUtil.Source includeSource,
            final EngineConfigurationUtil.Source excludeSource) {
        return fromPatterns(
                parseOptionalPattern(includeValue, includePresent, TAGS_INCLUDE_KEY, includeSource),
                parseOptionalPattern(excludeValue, excludePresent, TAGS_EXCLUDE_KEY, excludeSource),
                includeSource,
                excludeSource);
    }

    /**
     * Performs fromPatterns.
     *
     * @param includePatterns the includePatterns
     * @param excludePatterns the excludePatterns
     * @return the result
     */
    public static TagFilter fromPatterns(
            final @NonNull List<String> includePatterns, final @NonNull List<String> excludePatterns) {
        return fromPatterns(
                includePatterns,
                excludePatterns,
                EngineConfigurationUtil.Source.PROGRAMMATIC,
                EngineConfigurationUtil.Source.PROGRAMMATIC);
    }

    public static TagFilter fromPatterns(
            final @NonNull List<String> includePatterns,
            final @NonNull List<String> excludePatterns,
            final EngineConfigurationUtil.Source includeSource,
            final EngineConfigurationUtil.Source excludeSource) {
        Objects.requireNonNull(includePatterns, "includePatterns must not be null");
        Objects.requireNonNull(excludePatterns, "excludePatterns must not be null");

        return new RegexTagFilter(includePatterns, excludePatterns, includeSource, excludeSource);
    }

    private static List<String> parseOptionalPattern(
            final String rawValue,
            final boolean present,
            final String key,
            final EngineConfigurationUtil.Source source) {
        if (!present) {
            return Collections.emptyList();
        }

        final String normalized =
                EngineConfigurationUtil.normalizeProvided(key, rawValue == null ? "" : rawValue, source);
        return List.of(normalized);
    }
}
