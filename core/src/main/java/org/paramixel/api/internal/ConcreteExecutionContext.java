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

package org.paramixel.api.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.internal.action.MutableDescriptor;
import org.paramixel.api.internal.support.UnrecoverableErrors;
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;

/**
 * Concrete execution context for one descriptor invocation.
 */
public final class ConcreteExecutionContext implements ExecutionContext {

    private final Configuration configuration;
    private final Listener listener;
    private final MutableDescriptor descriptor;
    private final AsyncScheduler scheduler;
    private final InstanceHolder instanceHolder;

    /**
     * Creates an execution context.
     *
     * @param configuration the run configuration; must not be {@code null}
     * @param listener the run listener; must not be {@code null}
     * @param descriptor the active descriptor; must not be {@code null}
     * @param scheduler the scheduler; must not be {@code null}
     * @param instanceHolder the instance holder for this scope; must not be {@code null}
     */
    public ConcreteExecutionContext(
            final Configuration configuration,
            final Listener listener,
            final MutableDescriptor descriptor,
            final AsyncScheduler scheduler,
            final InstanceHolder instanceHolder) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.instanceHolder = Objects.requireNonNull(instanceHolder, "instanceHolder must not be null");
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public Listener listener() {
        return listener;
    }

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public <T> Optional<T> instance(final Class<T> type) {
        return Optional.ofNullable(instanceHolder.get(type));
    }

    @Override
    public void setStatus(final Status status) {
        descriptor.setStatus(Objects.requireNonNull(status, "status must not be null"));
    }

    /**
     * Schedules a direct child descriptor asynchronously.
     *
     * @param request the child execution request; must not be {@code null}
     * @return a future completed with the scheduled descriptor after execution
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws IllegalArgumentException if the requested descriptor is not a direct child
     */
    public CompletableFuture<Descriptor> scheduleAsync(final ExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!(request.descriptor() instanceof MutableDescriptor child)) {
            throw new IllegalArgumentException("descriptor was not created by this runner");
        }
        if (child.parent().orElse(null) != descriptor) {
            throw new IllegalArgumentException("can only schedule direct child descriptors");
        }
        return scheduler.schedule(child, request.mode(), this);
    }

    /**
     * Executes a direct child descriptor synchronously on the current thread.
     *
     * @param request the child execution request; must not be {@code null}
     * @return the executed descriptor
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws IllegalArgumentException if the requested descriptor is not a direct child
     */
    public Descriptor scheduleSync(final ExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!(request.descriptor() instanceof MutableDescriptor child)) {
            throw new IllegalArgumentException("descriptor was not created by this runner");
        }
        if (child.parent().orElse(null) != descriptor) {
            throw new IllegalArgumentException("can only schedule direct child descriptors");
        }
        child.markScheduled(request.mode());
        var childContext = new ConcreteExecutionContext(configuration, listener, child, scheduler, instanceHolder);
        scheduler.executeDescriptor(child, childContext);
        return child;
    }

    /**
     * Schedules a direct child descriptor synchronously, handling unrecoverable errors
     * and interruption.
     *
     * @param child the child descriptor to run; must not be {@code null}
     * @param mode the execution mode for the child; must not be {@code null}
     * @return the executed child descriptor
     */
    public Descriptor runChild(final Descriptor child, final Mode mode) {
        try {
            return scheduleSync(ExecutionRequest.of(child, mode));
        } catch (Throwable t) {
            var cause = unwrap(t);
            UnrecoverableErrors.rethrowIfUnrecoverable(cause);
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw cause instanceof RuntimeException re ? re : new RuntimeException(cause);
        }
    }

    /**
     * Schedules all direct children of the current descriptor synchronously.
     *
     * @param mode the execution mode for all children; must not be {@code null}
     */
    public void runChildren(final Mode mode) {
        for (Descriptor child : descriptor.children()) {
            runChild(child, mode);
        }
    }

    private static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        if (throwable instanceof RuntimeException rt && rt.getCause() != null) {
            var cause = rt.getCause();
            if (UnrecoverableErrors.isUnrecoverable(cause)
                    || cause instanceof Error
                    || cause instanceof InterruptedException) {
                return cause;
            }
        }
        return throwable;
    }

    /**
     * Returns a context for the same descriptor using a different instance holder.
     *
     * @param newInstanceHolder the holder to use; must not be {@code null}
     * @return the scoped context
     */
    public ConcreteExecutionContext withInstanceHolder(final InstanceHolder newInstanceHolder) {
        return new ConcreteExecutionContext(configuration, listener, descriptor, scheduler, newInstanceHolder);
    }

    /**
     * Returns the holder used to propagate fixture instances to child executions.
     *
     * @return the instance holder
     */
    public InstanceHolder instanceHolder() {
        return instanceHolder;
    }

    /**
     * Returns the scheduler for this execution context.
     *
     * @return the scheduler; never {@code null}
     * @see AsyncScheduler
     */
    public AsyncScheduler scheduler() {
        return scheduler;
    }
}
