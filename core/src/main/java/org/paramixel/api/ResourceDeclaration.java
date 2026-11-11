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

package org.paramixel.api;

import java.util.Objects;
import java.util.Optional;

/**
 * One declared secondary resource of a task: a typed key, a lazy factory, and an optional release callback.
 *
 * <p>A declaration is metadata. The factory is never invoked by definition, build, or compilation; it is invoked once
 * per execution attempt of the owning occurrence, in declaration order, after the occurrence was admitted.
 */
public final class ResourceDeclaration {

    private final Key<?> key;
    private final ResourceFactory factory;
    private final ResourceReleaser releaser;

    private ResourceDeclaration(final Key<?> key, final ResourceFactory factory, final ResourceReleaser releaser) {
        this.key = Objects.requireNonNull(key, "key is null");
        this.factory = Objects.requireNonNull(factory, "factory is null");
        this.releaser = releaser;
    }

    /**
     * Creates a declaration.
     *
     * @param key the resource key; must not be {@code null}
     * @param factory the lazy factory; must not be {@code null}
     * @param releaser the release callback, or {@code null} when the resource is externally owned
     * @return the declaration; never {@code null}
     * @throws NullPointerException if {@code key} or {@code factory} is {@code null}
     */
    public static ResourceDeclaration of(
            final Key<?> key, final ResourceFactory factory, final ResourceReleaser releaser) {
        return new ResourceDeclaration(key, factory, releaser);
    }

    /**
     * Returns the resource key.
     *
     * @return the key; never {@code null}
     */
    public Key<?> key() {
        return key;
    }

    /**
     * Returns the lazy factory.
     *
     * @return the factory; never {@code null}
     */
    public ResourceFactory factory() {
        return factory;
    }

    /**
     * Returns the release callback.
     *
     * @return the release callback, or an empty optional when the resource is externally owned
     */
    public Optional<ResourceReleaser> releaser() {
        return Optional.ofNullable(releaser);
    }

    @Override
    public String toString() {
        return "ResourceDeclaration[" + key.qualifiedName() + "]";
    }
}
