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

package org.paramixel.spi.action;

import java.util.Optional;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Descriptor;

/**
 * Provides runtime services for an active descriptor execution.
 *
 * <p>Actions use this context to read the active descriptor, access configuration and
 * fixture instances, and update their descriptor status.</p>
 */
public interface ExecutionContext {

    /**
     * Returns the configuration for the current run.
     *
     * @return the run configuration; never {@code null}
     */
    Configuration configuration();

    /**
     * Returns the listener for the current run.
     *
     * @return the listener; never {@code null}
     */
    Listener listener();

    /**
     * Returns the descriptor currently being executed.
     *
     * @return the active descriptor; never {@code null}
     */
    Descriptor descriptor();

    /**
     * Returns the current fixture instance for the requested type.
     *
     * @param <T> the requested instance type
     * @param type the requested instance class; must not be {@code null}
     * @return the instance, or empty when no compatible instance is available
     */
    <T> Optional<T> instance(Class<T> type);

    /**
     * Sets the active descriptor status.
     *
     * @param status the new descriptor status; must be {@link Status#RUNNING} or terminal
     * @throws NullPointerException if {@code status} is {@code null}
     * @throws IllegalArgumentException if {@code status} is {@link Status#PENDING}
     * @throws IllegalStateException if the transition is invalid
     */
    void setStatus(Status status);
}
