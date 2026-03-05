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
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.Paramixel;

/**
 * Filters test classes using regex patterns against {@code @Paramixel.Tags} values.
 *
 * <p>This filter supports include and exclude patterns using standard Java regular expressions.
 * Include patterns are applied first to select candidate classes, then exclude patterns remove
 * matching classes from the result set.</p>
 *
 * <p><b>Matching Behavior:</b></p>
 * <ul>
 *   <li>A class matches an include pattern if <strong>any</strong> of its tags matches the pattern</li>
 *   <li>A class is excluded if <strong>any</strong> of its tags matches an exclude pattern</li>
 *   <li>Multiple patterns are combined with OR logic (match any pattern)</li>
 *   <li>Classes without {@code @Tags} are only included when no include patterns are specified</li>
 * </ul>
 *
 * <p><b>Regex Examples:</b></p>
 * <pre>
 * "integration-.*"     - Matches tags starting with "integration-"
 * "^unit$"             - Matches exactly "unit"
 * ".*slow.*"           - Matches tags containing "slow"
 * "api-v2\\..*"        - Matches tags starting with "api-v2." (dot escaped)
 * </pre>
 *
 * @since 0.0.1
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class RegexTagFilter implements TagFilter {

    /**
     * Logger for tag filter diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(RegexTagFilter.class.getName());

    /**
     * Stores the includePatterns.
     */
    private final List<Pattern> includePatterns;
    /**
     * Stores the excludePatterns.
     */
    private final List<Pattern> excludePatterns;

    /**
     * Creates a tag filter with the specified regex patterns.
     *
     * <p>Patterns are compiled during construction. Invalid patterns are logged as warnings
     * and skipped.</p>
     *
     * @param includePatterns regex patterns for tags to include; may be empty but not {@code null}
     * @param excludePatterns regex patterns for tags to exclude; may be empty but not {@code null}
     * @since 0.0.1
     */
    public RegexTagFilter(final @NonNull List<String> includePatterns, final @NonNull List<String> excludePatterns) {
        Objects.requireNonNull(includePatterns, "includePatterns must not be null");
        Objects.requireNonNull(excludePatterns, "excludePatterns must not be null");
        this.includePatterns = compilePatterns(includePatterns, "include");
        this.excludePatterns = compilePatterns(excludePatterns, "exclude");
    }

    /**
     * Compiles a list of regex patterns, logging warnings for invalid patterns.
     *
     * @param patterns the pattern strings to compile
     * @param patternType the type of patterns (for logging)
     * @return list of compiled patterns; never {@code null}
     * @since 0.0.1
     */
    private List<Pattern> compilePatterns(final @NonNull List<String> patterns, final @NonNull String patternType) {
        final List<Pattern> compiled = new ArrayList<>();

        for (String pattern : patterns) {
            if (pattern == null || pattern.trim().isEmpty()) {
                continue;
            }

            try {
                compiled.add(Pattern.compile(pattern.trim()));
            } catch (PatternSyntaxException e) {
                throw new IllegalStateException(
                        "Invalid regex pattern in paramixel.tags." + patternType + ": \"" + pattern + "\"", e);
            }
        }

        return Collections.unmodifiableList(compiled);
    }

    @Override
    public boolean matches(final @NonNull Class<?> testClass) {
        Objects.requireNonNull(testClass, "testClass must not be null");

        final Set<String> classTags = extractAllTagsFromHierarchy(testClass);

        // Check exclude patterns first (highest priority)
        if (!excludePatterns.isEmpty()) {
            for (Pattern excludePattern : excludePatterns) {
                for (String tag : classTags) {
                    if (excludePattern.matcher(tag).matches()) {
                        return false;
                    }
                }
            }
        }

        // Check include patterns
        if (!includePatterns.isEmpty()) {
            for (Pattern includePattern : includePatterns) {
                for (String tag : classTags) {
                    if (includePattern.matcher(tag).matches()) {
                        return true;
                    }
                }
            }
            // Had include patterns but no match
            return false;
        }

        // No include patterns - class passes (assuming it passed excludes)
        return true;
    }

    /**
     * Extracts all valid tags from the class hierarchy.
     * Tags are collected from the test class and all its superclasses.
     * Tags with null or empty values are ignored.
     *
     * @param testClass the test class to extract tags from
     * @return set of valid tag strings from the entire hierarchy; empty set if no valid tags
     * @since 0.0.1
     */
    private Set<String> extractAllTagsFromHierarchy(final Class<?> testClass) {
        final Set<String> validTags = new java.util.HashSet<>();

        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            final Paramixel.Tags tags = current.getAnnotation(Paramixel.Tags.class);
            if (tags != null && tags.value() != null) {
                for (String tag : tags.value()) {
                    if (tag != null && !tag.trim().isEmpty()) {
                        validTags.add(tag);
                    }
                }
            }
        }

        return Collections.unmodifiableSet(validTags);
    }

    @Override
    public boolean hasIncludePatterns() {
        return !includePatterns.isEmpty();
    }

    /**
     * Returns the number of compiled include patterns.
     *
     * @return count of include patterns
     * @since 0.0.1
     */
    public int getIncludePatternCount() {
        return includePatterns.size();
    }

    /**
     * Returns the number of compiled exclude patterns.
     *
     * @return count of exclude patterns
     * @since 0.0.1
     */
    public int getExcludePatternCount() {
        return excludePatterns.size();
    }
}
