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
import java.util.function.Function;
import nonapi.org.paramixel.ExecutionMode;
import nonapi.org.paramixel.InstanceHolder;
import nonapi.org.paramixel.Scheduler;
import nonapi.org.paramixel.exception.UserCodeException;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Context;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;

/**
 * Concrete execution context for one descriptor invocation.
 */
public final class ConcreteContext implements Context {

    private final Configuration configuration;
    private final Listener listener;
    private final MutableDescriptor descriptor;
    private final Scheduler scheduler;
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
    public ConcreteContext(
            final Configuration configuration,
            final Listener listener,
            final MutableDescriptor descriptor,
            final Scheduler scheduler,
            final InstanceHolder instanceHolder) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.listener = Objects.requireNonNull(listener, "listener is null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor is null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null");
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
     * Returns the execution context used by the scheduler.
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
     * Schedules a direct child descriptor asynchronously.
     *
     * @param child the child descriptor to schedule; must not be {@code null}
     * @return a future completed with the scheduled descriptor after execution
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IllegalArgumentException if the descriptor is not directly attached
     */
    public CompletableFuture<Descriptor> scheduleAsync(final Descriptor child) {
        return scheduleAsync(child, ExecutionMode.RUN);
    }

    /**
     * Schedules a direct child descriptor asynchronously.
     *
     * @param child the child descriptor to schedule; must not be {@code null}
     * @param mode the execution mode for the child; must not be {@code null}
     * @return a future completed with the scheduled descriptor after execution
     * @throws NullPointerException if {@code child} or {@code mode} is {@code null}
     * @throws IllegalArgumentException if the descriptor is not directly attached
     */
    public CompletableFuture<Descriptor> scheduleAsync(final Descriptor child, final ExecutionMode mode) {
        return scheduler.schedule(requireDirectChild(child), Objects.requireNonNull(mode, "mode is null"), this);
    }

    /**
     * Executes a directly attached descriptor synchronously on the current thread.
     *
     * @param child the child descriptor to execute; must not be {@code null}
     * @param mode the execution mode for the child; must not be {@code null}
     * @return the executed descriptor
     * @throws NullPointerException if {@code child} or {@code mode} is {@code null}
     * @throws IllegalArgumentException if the descriptor is not directly attached
     */
    public Descriptor scheduleSync(final Descriptor child, final ExecutionMode mode) {
        var mutableChild = requireDirectChild(child);
        var executionMode = Objects.requireNonNull(mode, "mode is null");
        mutableChild.markScheduled();
        var childContext = new ConcreteContext(configuration, listener, mutableChild, scheduler, instanceHolder);
        scheduler.executeDescriptor(mutableChild, childContext, executionMode);
        return mutableChild;
    }

    /**
     * Schedules a direct child descriptor synchronously, handling unrecoverable errors
     * and interruption.
     *
     * <p>Any non-{@link RuntimeException} cause is wrapped in {@code RuntimeException} before
     * being thrown. This integrates with {@link UserCodeException#wrap}
     * at the API boundary, which peels exactly one {@code RuntimeException} layer from the
     * chain produced here, preserving the original semantic cause for {@link Status#fromThrowable}
     * classification.
     *
     * @param child the child descriptor to run; must not be {@code null}
     * @return the executed child descriptor
     */
    public Descriptor runChild(final Descriptor child) {
        return runChild(child, ExecutionMode.RUN);
    }

    /**
     * Schedules a direct child descriptor synchronously, handling unrecoverable errors
     * and interruption.
     *
     * @param child the child descriptor to run; must not be {@code null}
     * @param mode the execution mode for the child; must not be {@code null}
     * @return the executed child descriptor
     */
    public Descriptor runChild(final Descriptor child, final ExecutionMode mode) {
        try {
            return scheduleSync(child, mode);
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
     * @param mode the execution mode for each descriptor; must not be {@code null}
     */
    public void runChildren(final ExecutionMode mode) {
        Objects.requireNonNull(mode, "mode is null");
        runChildren(ignored -> mode);
    }

    /**
     * Schedules all directly attached descriptors of the current descriptor synchronously,
     * including before, body children, and after.
     *
     * @param modeFactory the execution mode factory for each descriptor; must not be {@code null}
     */
    public void runChildren(final Function<Descriptor, ExecutionMode> modeFactory) {
        Objects.requireNonNull(modeFactory, "modeFactory is null");
        descriptor.before().ifPresent(b -> runChild(b, requireMode(modeFactory, b)));
        for (Descriptor child : descriptor.children()) {
            runChild(child, requireMode(modeFactory, child));
        }
        descriptor.after().ifPresent(a -> runChild(a, requireMode(modeFactory, a)));
    }

    private static ExecutionMode requireMode(
            final Function<Descriptor, ExecutionMode> modeFactory, final Descriptor child) {
        return Objects.requireNonNull(modeFactory.apply(child), "mode is null");
    }

    private MutableDescriptor requireDirectChild(final Descriptor child) {
        Objects.requireNonNull(child, "child is null");
        if (!(child instanceof MutableDescriptor mutableChild)) {
            throw new IllegalArgumentException("descriptor was not created by this runner");
        }
        if (mutableChild.parent().orElse(null) != descriptor) {
            throw new IllegalArgumentException("can only schedule directly attached descriptors");
        }
        return mutableChild;
    }

    /**
     * Returns a context for the same descriptor using a different instance holder.
     *
     * @param newInstanceHolder the holder to use; must not be {@code null}
     * @return the scoped context
     */
    public ConcreteContext withInstanceHolder(final InstanceHolder newInstanceHolder) {
        return new ConcreteContext(configuration, listener, descriptor, scheduler, newInstanceHolder);
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
}
