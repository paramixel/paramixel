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
import java.util.function.Function;
import org.paramixel.api.action.Action;

final class DescriptorExpansionContext {

    private final MutableDescriptor descriptor;
    private final Function<Action, MutableDescriptor> descriptorFactory;

    DescriptorExpansionContext(
            final MutableDescriptor descriptor, final Function<Action, MutableDescriptor> descriptorFactory) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor is null");
        this.descriptorFactory = Objects.requireNonNull(descriptorFactory, "descriptorFactory is null");
    }

    void setBefore(final Action action) {
        descriptor.setBefore(descriptorFactory.apply(Objects.requireNonNull(action, "action is null")));
    }

    void addChild(final Action action) {
        descriptor.addChild(descriptorFactory.apply(Objects.requireNonNull(action, "action is null")));
    }

    void setAfter(final Action action) {
        descriptor.setAfter(descriptorFactory.apply(Objects.requireNonNull(action, "action is null")));
    }
}
