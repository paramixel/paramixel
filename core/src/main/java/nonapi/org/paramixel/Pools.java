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

package nonapi.org.paramixel;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;

/**
 * The named concurrency budgets of one run.
 *
 * <p>The runner's own root pool has the internal name {@value #ROOT_POOL_NAME}, which no user pool name can match,
 * and is sized by {@link Configuration#RUNNER_PARALLELISM}, defaulting to the number of available processors. Every
 * user pool {@code <name>} is sized by {@code paramixel.parallelism.<name>} and has no default: a pool a
 * {@code Parallel} names must be defined. Because the two namespaces are separate, a user pool may be called
 * {@code root} and is then sized by {@code paramixel.parallelism.root}.
 *
 * <p>A size is a positive integer, or {@code @cores}, {@code @cores*N} or {@code @cores/N}, which resolve once,
 * against the processors the JVM reports, to at least one. Pools are created on first use and live as long as this
 * registry, which is owned by one {@code Engine}.
 */
public final class Pools {

    /**
     * The internal name of the runner's root pool.
     *
     * <p>The leading {@code $} puts it outside the user pool-name grammar, so no {@code Parallel} a user writes can
     * alias it; {@code Parallel} accepts it only for the framework's own discovery body.
     */
    public static final String ROOT_POOL_NAME = "$root";

    static final String KEY_PREFIX = Configuration.RUNNER_PARALLELISM + ".";

    private static final Pattern CORES = Pattern.compile("@cores(?:([*/])(\\d+))?");

    private final Configuration configuration;
    private final ConcurrentHashMap<String, Pool> pools = new ConcurrentHashMap<>();

    Pools(final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
    }

    /**
     * Returns the configuration key that sizes a pool.
     *
     * @param name the pool name; must not be {@code null}
     * @return the key
     */
    static String keyOf(final String name) {
        return isRootPool(name) ? Configuration.RUNNER_PARALLELISM : KEY_PREFIX + name;
    }

    /**
     * Returns whether the supplied name is the runner's internal root pool.
     *
     * @param name the pool name; must not be {@code null}
     * @return {@code true} for the root pool
     */
    public static boolean isRootPool(final String name) {
        return ROOT_POOL_NAME.equals(name);
    }

    /**
     * Returns the named pool, creating it from configuration on first use.
     *
     * @param name the pool name; must not be {@code null}
     * @return the pool; never {@code null}
     * @throws ConfigurationException if the pool is not defined or its size is not a positive integer
     */
    Pool pool(final String name) {
        Objects.requireNonNull(name, "name is null");
        var existing = pools.get(name);
        if (existing != null) {
            return existing;
        }
        return pools.computeIfAbsent(name, n -> new Pool(n, sizeOf(n)));
    }

    private int sizeOf(final String name) {
        var key = keyOf(name);
        var raw = configuration.getString(key);
        if (raw.isEmpty()) {
            if (isRootPool(name)) {
                return resolveCores(1, true);
            }
            throw new ConfigurationException(
                    "Parallel pool '" + name + "' is not defined: set '" + key + "' to a positive integer or @cores");
        }
        var value = raw.get().trim();
        var cores = CORES.matcher(value);
        if (cores.matches()) {
            var operator = cores.group(1);
            if (operator == null) {
                return resolveCores(1, true);
            }
            long operand;
            try {
                operand = Long.parseLong(cores.group(2));
            } catch (NumberFormatException e) {
                throw invalid(key, raw.get(), e);
            }
            if (operand <= 0) {
                throw invalid(key, raw.get(), null);
            }
            return "*".equals(operator) ? resolveCores(operand, true) : resolveCores(operand, false);
        }
        final int size;
        try {
            size = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalid(key, raw.get(), e);
        }
        if (size <= 0) {
            throw invalid(key, raw.get(), null);
        }
        return size;
    }

    /**
     * Resolves {@code @cores}, {@code @cores*N} (multiply) or {@code @cores/N} (divide) against the number of
     * processors the JVM reports, never returning less than one.
     */
    private static int resolveCores(final long operand, final boolean multiply) {
        long cores = Runtime.getRuntime().availableProcessors();
        long resolved = multiply ? cores * operand : cores / operand;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, resolved));
    }

    /**
     * Returns the pools used by this run, in the order they were created.
     *
     * @return the pool statistics; never {@code null}
     */
    java.util.List<PoolStats> stats() {
        var stats = new java.util.ArrayList<PoolStats>(pools.size());
        for (var pool : pools.values()) {
            stats.add(new PoolStats(pool.name(), pool.size(), pool.peakInUse()));
        }
        stats.sort(java.util.Comparator.comparing(PoolStats::name));
        return java.util.List.copyOf(stats);
    }

    /**
     * One pool's size and peak usage, for the summary.
     *
     * @param name the pool name; never {@code null}
     * @param size the pool size
     * @param peakInUse the highest number of permits in use at the same time
     */
    public record PoolStats(String name, int size, int peakInUse) {}

    private static ConfigurationException invalid(final String key, final String value, final Throwable cause) {
        return new ConfigurationException(
                "Invalid value '" + value + "' for '" + key
                        + "': must be a positive integer, @cores, @cores*N or @cores/N (N a positive integer)",
                cause);
    }
}
