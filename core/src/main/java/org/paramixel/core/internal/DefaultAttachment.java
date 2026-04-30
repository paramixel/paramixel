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

package org.paramixel.core.internal;

import java.util.Objects;
import java.util.Optional;
import org.paramixel.core.Attachment;

final class DefaultAttachment implements Attachment {

    private final Object value;

    DefaultAttachment(Object value) {
        this.value = value;
    }

    @Override
    public Class<?> getType() {
        return value != null ? value.getClass() : Object.class;
    }

    @Override
    public <T> Optional<T> to(Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }
}
