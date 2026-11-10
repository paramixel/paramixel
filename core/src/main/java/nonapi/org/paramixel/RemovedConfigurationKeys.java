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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.paramixel.api.Configuration;

/**
 * Detects configuration keys that were removed and now have no effect.
 *
 * <p>A removed key is reported at most once per JVM: the effective configuration is JVM-wide, and a
 * Maven build creates one runner per discovered class, so per-run reporting would repeat the same
 * warning for every test class.
 *
 * <p>Detection uses presence only and never parses the value. A removed key must never fail a run,
 * including when the value is not a number.
 */
final class RemovedConfigurationKeys {

    private static final String QUEUE_CAPACITY = "paramixel.scheduler.queue.capacity";

    private static final Map<String, String> REMOVED = Map.of(
            QUEUE_CAPACITY,
            "Configuration '"
                    + QUEUE_CAPACITY
                    + "' was removed and has no effect: the scheduler ready queue no longer exists."
                    + " Remove the property. Use '"
                    + Configuration.RUNNER_PARALLELISM
                    + "' to bound concurrency.");

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private RemovedConfigurationKeys() {
        // Intentionally empty
    }

    /**
     * Returns warning messages for removed keys present in the supplied configuration.
     *
     * <p>Each removed key is reported at most once per JVM.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @return immutable list of warning messages; empty when nothing new was detected
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    static List<String> detect(final Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration is null");
        List<String> messages = null;
        for (var entry : REMOVED.entrySet()) {
            if (configuration.getString(entry.getKey()).isEmpty()) {
                continue;
            }
            if (!REPORTED.add(entry.getKey())) {
                continue;
            }
            if (messages == null) {
                messages = new ArrayList<>();
            }
            messages.add(entry.getValue());
        }
        return messages == null ? List.of() : List.copyOf(messages);
    }

    /**
     * Clears the once-per-JVM report state.
     *
     * <p>Test seam: the runner deliberately reports each removed key at most once per JVM, which
     * makes the behavior untestable without a way to reset the record.
     */
    static void resetForTesting() {
        REPORTED.clear();
    }
}
