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

import java.util.Objects;
import org.paramixel.api.Descriptor;
import org.paramixel.api.action.Mode;

/**
 * Describes a request to schedule one descriptor with a specific mode.
 *
 * @param descriptor the direct child descriptor to schedule
 * @param mode the mode for the child execution
 */
public record ExecutionRequest(Descriptor descriptor, Mode mode) {

    /**
     * Validates the scheduling request.
     *
     * @param descriptor the direct child descriptor to schedule
     * @param mode the mode for the child execution
     * @throws NullPointerException if any component is {@code null}
     */
    public ExecutionRequest {
        Objects.requireNonNull(descriptor, "descriptor is null");
        Objects.requireNonNull(mode, "mode is null");
    }

    /**
     * Creates a request for the supplied descriptor and mode.
     *
     * @param descriptor the descriptor to schedule
     * @param mode the execution mode
     * @return the scheduling request
     */
    public static ExecutionRequest of(final Descriptor descriptor, final Mode mode) {
        return new ExecutionRequest(descriptor, mode);
    }

    /**
     * Creates a run-mode request for the supplied descriptor.
     *
     * @param descriptor the descriptor to schedule
     * @return the scheduling request
     */
    public static ExecutionRequest run(final Descriptor descriptor) {
        return new ExecutionRequest(descriptor, Mode.RUN);
    }

    /**
     * Creates a skip-mode request for the supplied descriptor.
     *
     * @param descriptor the descriptor to schedule
     * @return the scheduling request
     */
    public static ExecutionRequest skip(final Descriptor descriptor) {
        return new ExecutionRequest(descriptor, Mode.SKIP);
    }

    /**
     * Creates an abort-mode request for the supplied descriptor.
     *
     * @param descriptor the descriptor to schedule
     * @return the scheduling request
     */
    public static ExecutionRequest abort(final Descriptor descriptor) {
        return new ExecutionRequest(descriptor, Mode.ABORT);
    }
}
