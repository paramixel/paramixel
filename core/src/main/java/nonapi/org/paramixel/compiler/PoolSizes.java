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

package nonapi.org.paramixel.compiler;

import java.util.regex.Pattern;

/**
 * Parses pool capacities.
 *
 * <p>A capacity is a positive integer, or one of the processor-relative expressions {@code @cores},
 * {@code @cores*N} (multiply) and {@code @cores/N} (divide). Processor-relative expressions resolve once against
 * {@link Runtime#availableProcessors()} and never resolve below one, so a small container cannot configure an
 * unusable pool. Division truncates toward zero.
 */
final class PoolSizes {

    private static final Pattern CORES = Pattern.compile("@cores(?:([*/])(\\d+))?");

    private PoolSizes() {
        // Intentionally empty
    }

    /**
     * Parses a capacity.
     *
     * @param raw the raw configured value; must not be {@code null}
     * @return the resolved capacity; at least one
     * @throws IllegalArgumentException if the value is not a valid capacity
     */
    static int parse(final String raw) {
        final var value = raw.strip();
        final var matcher = CORES.matcher(value);
        if (matcher.matches()) {
            final var operator = matcher.group(1);
            if (operator == null) {
                return resolveCores(1, true);
            }
            final long operand;
            try {
                operand = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("not a number: " + matcher.group(2), e);
            }
            if (operand <= 0) {
                throw new IllegalArgumentException("operand must be positive: " + operand);
            }
            return "*".equals(operator) ? resolveCores(operand, true) : resolveCores(operand, false);
        }
        if (value.startsWith("@cores")) {
            throw new IllegalArgumentException("unsupported core expression: " + value);
        }
        final int capacity;
        try {
            capacity = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not an integer: " + value, e);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("must be positive: " + capacity);
        }
        return capacity;
    }

    private static int resolveCores(final long operand, final boolean multiply) {
        final long cores = Runtime.getRuntime().availableProcessors();
        final long resolved = multiply ? cores * operand : cores / operand;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, resolved));
    }
}
