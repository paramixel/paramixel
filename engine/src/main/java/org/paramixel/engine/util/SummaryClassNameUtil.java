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

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Provides utilities for rendering class names in the Maven-only engine summary table.
 *
 * <p>The summary table supports a maximum class-name length configuration via
 * {@code paramixel.summary.classNameMaxLength}. When a maximum is configured, this utility
 * abbreviates package segments while preserving the final segment intact.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class SummaryClassNameUtil {

    /**
     * Minimum allowed configured maximum length.
     */
    public static final int MIN_MAX_LENGTH = 1;

    /**
     * Maximum allowed configured maximum length.
     */
    public static final int MAX_MAX_LENGTH = Integer.MAX_VALUE;

    /**
     * Creates a new instance.
     */
    private SummaryClassNameUtil() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Abbreviates a fully-qualified class name to fit within {@code maxLength}.
     *
     * <p>Rules:
     *
     * <ul>
     *   <li>The final segment (after the last {@code '.'}) is always kept intact.
     *   <li>All other segments are either kept intact or abbreviated to exactly 1 character.
     *   <li>When possible, segments are expanded from right to left to preserve as much context as possible
     *       while staying within the configured maximum.
     * </ul>
     *
     * @param propertyKey the property key used in error messages; never {@code null}
     * @param fullClassName the class name to abbreviate; never {@code null}
     * @param maxLength the configured maximum length
     * @return the abbreviated class name
     */
    public static String abbreviateClassName(
            final @NonNull String propertyKey, final @NonNull String fullClassName, final int maxLength) {
        Objects.requireNonNull(propertyKey, "propertyKey must not be null");
        Objects.requireNonNull(fullClassName, "fullClassName must not be null");

        if (maxLength == Integer.MAX_VALUE) {
            return fullClassName;
        }

        final String[] segments = fullClassName.split("\\.", -1);
        if (segments.length <= 1) {
            return fullClassName;
        }

        final String lastSegment = segments[segments.length - 1];
        final String minimal = buildMinimalAbbreviation(segments);
        if (minimal.length() > maxLength) {
            return minimal;
        }

        final String[] rendered = new String[segments.length];
        for (int i = 0; i < segments.length - 1; i++) {
            rendered[i] = abbreviateSegmentToOneCharacter(segments[i]);
        }
        rendered[segments.length - 1] = lastSegment;

        int currentLength = minimal.length();
        for (int i = segments.length - 2; i >= 0; i--) {
            final String original = segments[i];
            final String abbreviated = rendered[i];

            if (original.equals(abbreviated)) {
                continue;
            }

            final int delta = original.length() - abbreviated.length();
            if (currentLength + delta <= maxLength) {
                rendered[i] = original;
                currentLength += delta;
            } else {
                // Stop expanding; preserve the most-informative right-hand segments.
                break;
            }
        }

        return String.join(".", rendered);
    }

    /**
     * Builds the minimal abbreviation for the provided segments.
     *
     * @param segments the split class name segments
     * @return the minimal abbreviation string
     */
    private static String buildMinimalAbbreviation(final @NonNull String[] segments) {
        Objects.requireNonNull(segments, "segments must not be null");

        final String[] minimal = new String[segments.length];
        for (int i = 0; i < segments.length - 1; i++) {
            minimal[i] = abbreviateSegmentToOneCharacter(segments[i]);
        }
        minimal[segments.length - 1] = segments[segments.length - 1];
        return String.join(".", minimal);
    }

    /**
     * Abbreviates a segment to exactly 1 character when possible.
     *
     * @param segment the segment to abbreviate
     * @return the abbreviated segment
     */
    private static String abbreviateSegmentToOneCharacter(final @NonNull String segment) {
        Objects.requireNonNull(segment, "segment must not be null");

        if (segment.isEmpty()) {
            return segment;
        }
        return segment.substring(0, 1);
    }
}
