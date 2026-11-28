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

package nonapi.org.paramixel.reporting;

import java.io.PrintStream;
import java.util.ArrayList;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;

/**
 * Builds the default listener chain.
 *
 * <p>The chain contains status output, the summary, and a report file writer when {@link Configuration#REPORT_FILE} is
 * set. The same configuration governs ANSI formatting and which console sections are suppressed.
 */
public final class DefaultListeners {

    private DefaultListeners() {
        // Intentionally empty
    }

    /**
     * Creates the default listener chain.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @return the listener; never {@code null}
     */
    public static Listener create(final Configuration configuration) {
        return create(configuration, System.out);
    }

    /**
     * Creates the default listener chain writing to the supplied stream.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @param out the console stream; must not be {@code null}
     * @return the listener; never {@code null}
     */
    public static Listener create(final Configuration configuration, final PrintStream out) {
        final var listeners = new ArrayList<Listener>();
        listeners.add(new StatusListener(configuration, out));
        listeners.add(new SummaryListener(configuration, out));
        configuration
                .getString(Configuration.REPORT_FILE)
                .filter(value -> !value.isBlank())
                .ifPresent(value -> listeners.add(new ReportFileListener(configuration)));
        return new CompositeListener(listeners);
    }
}
