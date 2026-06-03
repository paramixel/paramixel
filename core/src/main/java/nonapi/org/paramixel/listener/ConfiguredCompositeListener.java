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

package nonapi.org.paramixel.listener;

import java.util.List;
import java.util.Objects;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;

/**
 * Composite listener that initializes delegates with the configuration captured at construction time.
 */
public final class ConfiguredCompositeListener extends CompositeListener {

    private final Configuration configuration;

    /**
     * Creates a configured composite listener.
     *
     * @param configuration the configuration used to initialize delegates; must not be {@code null}
     * @param listeners the listeners to invoke
     * @param ansiEnabled whether ANSI diagnostics should be used
     */
    public ConfiguredCompositeListener(
            final Configuration configuration, final List<Listener> listeners, final boolean ansiEnabled) {
        super(listeners, ansiEnabled);
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
    }

    @Override
    public void initialize(final Configuration ignored) {
        super.initialize(configuration);
    }
}
