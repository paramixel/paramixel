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
import java.util.concurrent.CompletableFuture;
import nonapi.org.paramixel.ExecutionRequest;
import nonapi.org.paramixel.InstanceHolder;
import nonapi.org.paramixel.Scheduler;
import nonapi.org.paramixel.TopLevelParallelThrottle;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Context;
import org.paramixel.api.action.Mode;

/**
 * Concrete execution context for one descriptor invocation.
 */
public final class ConcreteContext implements Context {

    private final Configuration configuration;
    private final Listener listener;
    private final MutableDescriptor descriptor;
    private final Scheduler scheduler;
    private final InstanceHolder instanceHolder;
    private final TopLevelParallelThrottle topLevelParallelThrottle;

    /**
     * Creates an execution context.
     *
     * @param configuration the run configuration; must not be {@code null}
     * @param listener the run listener; must not be {@code null}
     * @param descriptor the active descriptor; must not be {@code null}
     * @param scheduler the scheduler; must not be {@code null}
     * @param instanceHolder the instance holder for this scope; must not be {@code null}
     */
    public ConcreteContext(
            final Configuration configuration,
            final Listener listener,
            final MutableDescriptor descriptor,
            final Scheduler scheduler,
            final InstanceHolder instanceHolder) {
        this(configuration, listener, descriptor, scheduler, instanceHolder, null);
    }

    /**
     * Creates an execution context with an optional top-level parallel throttle.
     *
     * @param configuration the run configuration; must not be {@code null}
     * @param listener the run listener; must not be {@code null}
     * @param descriptor the active descriptor; must not be {@code null}
     * @param scheduler the scheduler; must not be {@code null}
     * @param instanceHolder the instance holder for this scope; must not be {@code null}
     * @param topLevelParallelThrottle optional root-parallel throttle; may be {@code null}
     */
    public ConcreteContext(
            final Configuration configuration,
            final Listener listener,
            final MutableDescriptor descriptor,
            final Scheduler scheduler,
            final InstanceHolder instanceHolder,
            final TopLevelParallelThrottle topLevelParallelThrottle) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.listener = Objects.requireNonNull(listener, "listener is null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor is null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null");
        this.instanceHolder = Objects.requireNonNull(instanceHolder, "instanceHolder is null");
        this.topLevelParallelThrottle = topLevelParallelThrottle;
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
        descriptor.setStatus(Objects.requireNonNull(status, "status is null"));
    }

    /**
     * Schedules a direct child descriptor asynchronously.
     *
     * @param request the child execution request; must not be {@code null}
     * @return a future completed with the scheduled descriptor after execution
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws IllegalArgumentException if the requested descriptor is not directly attached
     */
    public CompletableFuture<Descriptor> scheduleAsync(final ExecutionRequest request) {
        Objects.requireNonNull(request, "request is null");
        if (!(request.descriptor() instanceof MutableDescriptor child)) {
            throw new IllegalArgumentException("descriptor was not created by this runner");
        }
        if (child.parent().orElse(null) != descriptor) {
            throw new IllegalArgumentException("can only schedule directly attached descriptors");
        }
        return scheduler.schedule(child, request.mode(), this);
    }

    /**
     * Executes a directly attached descriptor synchronously on the current thread.
     *
     * @param request the child execution request; must not be {@code null}
     * @return the executed descriptor
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws IllegalArgumentException if the requested descriptor is not directly attached
     */
    public Descriptor scheduleSync(final ExecutionRequest request) {
        Objects.requireNonNull(request, "request is null");
        if (!(request.descriptor() instanceof MutableDescriptor child)) {
            throw new IllegalArgumentException("descriptor was not created by this runner");
        }
        if (child.parent().orElse(null) != descriptor) {
            throw new IllegalArgumentException("can only schedule directly attached descriptors");
        }
        child.markScheduled(request.mode());
        var childContext = new ConcreteContext(
                configuration, listener, child, scheduler, instanceHolder, topLevelParallelThrottle);
        scheduler.executeDescriptor(child, childContext);
        return child;
    }

    /**
     * Schedules a direct child descriptor synchronously, handling unrecoverable errors
     * and interruption.
     *
     * <p>Any non-{@link RuntimeException} cause is wrapped in {@code RuntimeException} before
     * being thrown. This integrates with {@link nonapi.org.paramixel.FrameworkException#wrap}
     * at the API boundary, which peels exactly one {@code RuntimeException} layer from the
     * chain produced here, preserving the original semantic cause for {@link Status#fromThrowable}
     * classification.
     *
     * @param child the child descriptor to run; must not be {@code null}
     * @param mode the execution mode for the child; must not be {@code null}
     * @return the executed child descriptor
     */
    public Descriptor runChild(final Descriptor child, final Mode mode) {
        try {
            return scheduleSync(ExecutionRequest.of(child, mode));
        } catch (Throwable t) {
            var cause = Throwables.unwrap(t);
            UnrecoverableErrors.rethrowIfUnrecoverable(cause);
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw cause instanceof RuntimeException re ? re : new RuntimeException(cause);
        }
    }

    /**
     * Schedules all directly attached descriptors of the current descriptor synchronously,
     * including before, body children, and after.
     *
     * @param mode the execution mode for all descriptors; must not be {@code null}
     */
    public void runChildren(final Mode mode) {
        descriptor.before().ifPresent(b -> runChild(b, mode));
        for (Descriptor child : descriptor.children()) {
            runChild(child, mode);
        }
        descriptor.after().ifPresent(a -> runChild(a, mode));
    }

    /**
     * Returns a context for the same descriptor using a different instance holder.
     *
     * @param newInstanceHolder the holder to use; must not be {@code null}
     * @return the scoped context
     */
    public ConcreteContext withInstanceHolder(final InstanceHolder newInstanceHolder) {
        return new ConcreteContext(
                configuration, listener, descriptor, scheduler, newInstanceHolder, topLevelParallelThrottle);
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
     * @see Scheduler
     */
    public Scheduler scheduler() {
        return scheduler;
    }

    /**
     * Returns whether this context has a root-parallel throttle.
     *
     * @return {@code true} when a throttle is configured
     */
    public boolean hasTopLevelParallelThrottle() {
        return topLevelParallelThrottle != null;
    }

    /**
     * Acquires one root-parallel throttle permit.
     *
     * @throws IllegalStateException if no top-level throttle is configured
     * @throws InterruptedException if interrupted while waiting
     */
    public void acquireTopLevelParallelPermit() throws InterruptedException {
        if (topLevelParallelThrottle == null) {
            throw new IllegalStateException("No top-level parallel throttle configured");
        }
        topLevelParallelThrottle.acquire();
    }

    /**
     * Releases one root-parallel throttle permit.
     *
     * @throws IllegalStateException if no top-level throttle is configured
     */
    public void releaseTopLevelParallelPermit() {
        if (topLevelParallelThrottle == null) {
            throw new IllegalStateException("No top-level parallel throttle configured");
        }
        topLevelParallelThrottle.release();
    }
}
