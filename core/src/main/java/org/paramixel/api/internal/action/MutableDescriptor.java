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

package org.paramixel.api.internal.action;

import java.util.concurrent.CompletableFuture;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Descriptor;
import org.paramixel.spi.action.Mode;

/**
 * Internal mutable extension of {@link Descriptor} for framework use during
 * discovery and execution.
 *
 * <p>Public consumers interact with the read-only {@link Descriptor} view.
 * This interface exposes mutation operations needed by the framework to build
 * descriptor trees and manage execution state.
 */
public interface MutableDescriptor extends Descriptor {

    /**
     * Returns the precomputed scheduler priority key for this descriptor.
     *
     * <p>The key is assigned during discovery and represents this descriptor's
     * stable position in the descriptor tree.</p>
     *
     * @return the scheduler priority key; never {@code null}
     */
    SchedulerPriorityKey schedulerPriorityKey();

    /**
     * Sets the precomputed scheduler priority key for this descriptor.
     *
     * @param schedulerPriorityKey the scheduler priority key; must not be {@code null}
     */
    void setSchedulerPriorityKey(SchedulerPriorityKey schedulerPriorityKey);

    /**
     * Returns the action definition bound to this descriptor occurrence.
     *
     * @return the action; never {@code null}
     */
    Action<?> action();

    /**
     * Adds a child descriptor during discovery.
     *
     * @param child the child descriptor; must not be {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IllegalArgumentException if {@code child} is this descriptor
     */
    void addChild(MutableDescriptor child);

    /**
     * Sets the parent descriptor. Called during discovery to establish
     * the tree relationship.
     *
     * @param parent the parent descriptor; must not be {@code null}
     */
    void setParent(MutableDescriptor parent);

    /**
     * Freezes this descriptor's tree structure and caches immutable child views.
     *
     * <p>After freeze, structural mutations such as adding children or re-parenting
     * are rejected with {@link IllegalStateException}. Implementations should apply
     * freeze recursively to descendants.
     */
    void freeze();

    /**
     * Returns whether this descriptor is frozen.
     *
     * @return {@code true} when the descriptor is frozen
     */
    boolean isFrozen();

    /**
     * Prepares this descriptor for scheduling.
     *
     * @param requestedMode the requested execution mode; must not be {@code null}
     * @return a future associated with this scheduled descriptor
     * @throws IllegalStateException if already scheduled
     */
    CompletableFuture<Descriptor> markScheduled(Mode requestedMode);

    /**
     * Returns whether this descriptor has already been scheduled.
     *
     * @return {@code true} when scheduled
     */
    boolean isScheduled();

    /**
     * Sets this descriptor's local execution status.
     *
     * @param newStatus the new status; must not be {@code null}
     * @throws NullPointerException if {@code newStatus} is {@code null}
     */
    void setStatus(Status newStatus);

    /**
     * Completes the scheduling future.
     */
    void completeFuture();

    /**
     * Completes the scheduling future exceptionally.
     *
     * @param throwable the failure; must not be {@code null}
     */
    void completeFutureExceptionally(Throwable throwable);
}
