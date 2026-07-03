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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;

/**
 * Immutable implementation of {@link Configuration} backed by a map of string key-value pairs.
 *
 * <p>All typed accessors parse from the underlying string values. Absent keys return
 * {@link Optional#empty()}. Present values that fail parsing throw {@link ConfigurationException}.
 */
public final class ConcreteConfiguration implements Configuration {

    private final Map<String, String> properties;

    /**
     * Creates a configuration backed by the supplied map.
     *
     * <p>The supplied map is defensively copied; subsequent changes to the original map are not reflected in this configuration.
     *
     * @param properties the backing property map; must not be {@code null}
     */
    public ConcreteConfiguration(final Map<String, String> properties) {
        this.properties = Map.copyOf(Objects.requireNonNull(properties, "properties is null"));
    }

    @Override
    public Optional<String> getString(final String key) {
        Objects.requireNonNull(key, "key is null");
        return Optional.ofNullable(properties.get(key));
    }

    @Override
    public Optional<Boolean> getBoolean(final String key) {
        Objects.requireNonNull(key, "key is null");
        var value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Configuration.parseBoolean(value));
    }

    @Override
    public Optional<Integer> getInteger(final String key) {
        Objects.requireNonNull(key, "key is null");
        var value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "Invalid configuration for '" + key + "': expected integer but was '" + value + "'", e);
        }
    }

    @Override
    public Optional<Long> getLong(final String key) {
        Objects.requireNonNull(key, "key is null");
        var value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.strip()));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "Invalid configuration for '" + key + "': expected long but was '" + value + "'", e);
        }
    }

    @Override
    public Optional<Float> getFloat(final String key) {
        Objects.requireNonNull(key, "key is null");
        var value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Float.parseFloat(value.strip()));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "Invalid configuration for '" + key + "': expected float but was '" + value + "'", e);
        }
    }

    @Override
    public Optional<Double> getDouble(final String key) {
        Objects.requireNonNull(key, "key is null");
        var value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value.strip()));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "Invalid configuration for '" + key + "': expected double but was '" + value + "'", e);
        }
    }

    @Override
    public <T> Optional<T> get(final String key, final Function<String, T> transformer) {
        Objects.requireNonNull(key, "key is null");
        Objects.requireNonNull(transformer, "transformer is null");
        var value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(transformer.apply(value));
        } catch (Exception e) {
            throw new ConfigurationException("Invalid configuration for '" + key + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Set<String> keySet() {
        return properties.keySet();
    }

    /**
     * Returns the underlying property map for internal use by Resolver and ActionResolver.
     *
     * <p>This method exists solely to bridge the transition period where internal components
     * still accept {@code Map<String, String>}. It is not part of the public API.
     *
     * @return an unmodifiable view of the backing property map; never {@code null}
     */
    public Map<String, String> toMap() {
        return properties;
    }
}
