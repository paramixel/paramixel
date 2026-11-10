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

package nonapi.org.paramixel.action;

import java.util.Objects;
import java.util.Optional;
import nonapi.org.paramixel.InstanceHolder;
import org.paramixel.api.Configuration;
import org.paramixel.api.Context;
import org.paramixel.api.Listener;

/**
 * Concrete execution context for one descriptor invocation.
 *
 * <p>The context carries the data user code may read: the effective configuration, the run
 * listener, the active descriptor, and the scoped instance holder. Traversal is owned by the
 * execution engine, so the context exposes no scheduling operations.
 */
public final class ConcreteContext implements Context {

    private final Configuration configuration;
    private final Listener listener;
    private final MutableDescriptor descriptor;
    private final InstanceHolder instanceHolder;

    /**
     * Creates an execution context.
     *
     * @param configuration the run configuration; must not be {@code null}
     * @param listener the run listener; must not be {@code null}
     * @param descriptor the active descriptor; must not be {@code null}
     * @param instanceHolder the instance holder for this scope; must not be {@code null}
     */
    public ConcreteContext(
            final Configuration configuration,
            final Listener listener,
            final MutableDescriptor descriptor,
            final InstanceHolder instanceHolder) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.listener = Objects.requireNonNull(listener, "listener is null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor is null");
        this.instanceHolder = Objects.requireNonNull(instanceHolder, "instanceHolder is null");
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    /**
     * Returns the listener for this execution context.
     *
     * @return the listener; never {@code null}
     */
    public Listener listener() {
        return listener;
    }

    /**
     * Returns the active descriptor for this execution context.
     *
     * @return the descriptor; never {@code null}
     */
    public MutableDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public <T> Optional<T> instance(final Class<T> type) {
        return Optional.ofNullable(instanceHolder.get(type));
    }

    /**
     * Returns the execution context used by the engine.
     *
     * @param context the public action context; must not be {@code null}
     * @return the execution context
     * @throws IllegalArgumentException if the context was not created by this runner
     */
    public static ConcreteContext require(final Context context) {
        Objects.requireNonNull(context, "context is null");
        if (context instanceof ConcreteContext concreteContext) {
            return concreteContext;
        }
        throw new IllegalArgumentException("context must be a ConcreteContext");
    }

    /**
     * Returns a context for the same descriptor using a different instance holder.
     *
     * @param newInstanceHolder the holder to use; must not be {@code null}
     * @return the scoped context
     */
    public ConcreteContext withInstanceHolder(final InstanceHolder newInstanceHolder) {
        return new ConcreteContext(configuration, listener, descriptor, newInstanceHolder);
    }

    /**
     * Returns the holder used to propagate fixture instances to child executions.
     *
     * @return the instance holder
     */
    public InstanceHolder instanceHolder() {
        return instanceHolder;
    }
}
