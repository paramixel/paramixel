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

import java.util.List;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;

/**
 * Test helper: builds descriptor trees the way the compile pass does, bottom-up, so tests never need the removed
 * structural mutators.
 */
public final class TestDescriptors {

    private TestDescriptors() {
        // Intentionally empty
    }

    /**
     * Returns a standalone, fully shaped descriptor with no lifecycle slots or children.
     *
     * @param action the action to bind; must not be {@code null}
     * @return the descriptor
     */
    public static ConcreteDescriptor of(final Action action) {
        return new ConcreteDescriptor(action);
    }

    /**
     * Returns a descriptor with the supplied structural slots, already linked.
     *
     * @param action the action to bind; must not be {@code null}
     * @param before the before slot, or {@code null}
     * @param children the body children; must not be {@code null}
     * @param after the after slot, or {@code null}
     * @return the descriptor
     */
    public static ConcreteDescriptor tree(
            final Action action,
            final MutableDescriptor before,
            final List<? extends Descriptor> children,
            final MutableDescriptor after) {
        var descriptor = new ConcreteDescriptor(action, before, children, after);
        descriptor.attachSlots();
        return descriptor;
    }

    /**
     * Returns a descriptor with body children and no lifecycle slots, already linked.
     *
     * @param action the action to bind; must not be {@code null}
     * @param children the body children; must not be {@code null}
     * @return the descriptor
     */
    public static ConcreteDescriptor tree(final Action action, final List<? extends Descriptor> children) {
        return tree(action, null, children, null);
    }

    /**
     * Moves a descriptor to a terminal status through the RUNNING state.
     *
     * @param descriptor the descriptor to transition; must not be {@code null}
     * @param status the terminal status; must not be {@code null}
     * @return the same descriptor
     */
    public static ConcreteDescriptor completed(final ConcreteDescriptor descriptor, final Status status) {
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(status);
        return descriptor;
    }
}
