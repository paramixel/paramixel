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

import java.util.List;
import java.util.Optional;
import org.paramixel.api.action.Metadata;

/**
 * Describes one bound occurrence of an action in a run descriptor tree.
 *
 * <p>Descriptors are read-only through this public interface. The framework creates and
 * mutates concrete descriptors during discovery and execution. Reusing the same {@link org.paramixel.api.action.Action}
 * instance in multiple locations creates distinct descriptors with independent execution state.
 *
 * <p>Structurally mirrors {@link org.paramixel.api.action.Action}: {@link #before()} and
 * {@link #after()} are separate structural slots, not included in {@link #children()}.
 * The descriptor provides tree navigation ({@link #parent()}, {@link #before()},
 * {@link #children()}, {@link #after()}) and live execution-occurrence metadata
 * ({@link #metadata()}). Execution state (status, mode, run duration, message, throwable)
 * is accessed via {@link #metadata()} rather than directly on the descriptor.
 */
public interface Descriptor {

    /**
     * Returns this descriptor's parent in the descriptor tree.
     *
     * @return the parent descriptor, or empty for the root descriptor
     */
    Optional<Descriptor> parent();

    /**
     * Returns metadata for this descriptor execution occurrence.
     *
     * <p>The metadata carries the execution-occurrence identifier, display name, kind,
     * class name, and live execution state (status, mode, run duration, message, throwable).
     *
     * @return the metadata; never {@code null}
     */
    Metadata metadata();

    /**
     * Returns the before-child descriptor, if this descriptor's action declares one.
     *
     * <p>The before-child is a separate structural slot and is not included in
     * {@link #children()}. Composite actions such as {@link org.paramixel.api.action.Lifecycle},
     * {@link org.paramixel.api.action.Static}, and {@link org.paramixel.api.action.Instance}
     * override this method to return their before-child. Leaf and decorator descriptors
     * inherit the default, which returns an empty optional.
     *
     * @return the before-child descriptor, or empty if none
     */
    default Optional<Descriptor> before() {
        return Optional.empty();
    }

    /**
     * Returns this descriptor's direct body children in discovery order.
     *
     * <p>Body children do not include the {@link #before()} or {@link #after()}
     * descriptors — those are separate structural slots.
     *
     * @return immutable ordered body child descriptors; never {@code null} and contains no {@code null} elements
     */
    List<Descriptor> children();

    /**
     * Returns the after-child descriptor, if this descriptor's action declares one.
     *
     * <p>The after-child is a separate structural slot and is not included in
     * {@link #children()}. Composite actions such as {@link org.paramixel.api.action.Lifecycle},
     * {@link org.paramixel.api.action.Static}, and {@link org.paramixel.api.action.Instance}
     * override this method to return their after-child. Leaf and decorator descriptors
     * inherit the default, which returns an empty optional.
     *
     * @return the after-child descriptor, or empty if none
     */
    default Optional<Descriptor> after() {
        return Optional.empty();
    }
}
