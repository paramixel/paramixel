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

package org.paramixel.maven.plugin.internal.util;

import java.util.Objects;
import org.paramixel.core.support.Arguments;

public class ClassNameUtil {

    private ClassNameUtil() {
        // Intentionally empty
    }

    public static String abbreviateClassName(final String fullClassName, final int maxLength) {
        Objects.requireNonNull(fullClassName, "fullClassName must not be null");
        Arguments.requireNonBlank(fullClassName, "fullClassName must not be blank");
        Arguments.requirePositive(maxLength, "maxLength must be positive");

        if (maxLength == Integer.MAX_VALUE) {
            return fullClassName;
        }

        final var segments = fullClassName.split("\\.", -1);
        if (segments.length <= 1) {
            return fullClassName;
        }

        final var lastSegment = segments[segments.length - 1];
        final var minimal = buildMinimalAbbreviation(segments);
        if (minimal.length() > maxLength) {
            return minimal;
        }

        final var rendered = new String[segments.length];
        for (var i = 0; i < segments.length - 1; i++) {
            rendered[i] = abbreviateSegmentToOneCharacter(segments[i]);
        }
        rendered[segments.length - 1] = lastSegment;

        var currentLength = minimal.length();
        for (var i = segments.length - 2; i >= 0; i--) {
            final var original = segments[i];
            final var abbreviated = rendered[i];

            if (original.equals(abbreviated)) {
                continue;
            }

            final var delta = original.length() - abbreviated.length();
            if (currentLength + delta <= maxLength) {
                rendered[i] = original;
                currentLength += delta;
            } else {
                break;
            }
        }

        return String.join(".", rendered);
    }

    private static String buildMinimalAbbreviation(final String[] segments) {
        Objects.requireNonNull(segments, "segments must not be null");

        final var minimal = new String[segments.length];
        for (var i = 0; i < segments.length - 1; i++) {
            minimal[i] = abbreviateSegmentToOneCharacter(segments[i]);
        }
        minimal[segments.length - 1] = segments[segments.length - 1];
        return String.join(".", minimal);
    }

    private static String abbreviateSegmentToOneCharacter(final String segment) {
        Objects.requireNonNull(segment, "segment must not be null");

        if (segment.isEmpty()) {
            return segment;
        }
        return segment.substring(0, 1);
    }
}
