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

/**
 * Internal holder for per-execution instance objects created by
 * {@link org.paramixel.api.action.Instance} actions.
 *
 * <p>The holder is shared by reference between parent and child
 * {@link nonapi.org.paramixel.action.ConcreteContext} instances so that instance objects are visible
 * to before-actions, body children, and after-actions within the same lifecycle.
 *
 * <p>Instance objects are set by {@code Instance.execute()} and cleared by
 * destroy actions via {@link #clear()}. The holder is not part of the public API.
 */
public final class InstanceHolder {

    private volatile Object instance;

    /**
     * Creates an empty instance holder.
     */
    public InstanceHolder() {
        this.instance = null;
    }

    /**
     * Stores the supplied instance in this holder.
     *
     * @param instance the instance to expose, or {@code null} to make the holder empty
     */
    public void set(final Object instance) {
        this.instance = instance;
    }

    /**
     * Returns the held instance when it is assignable to the requested type.
     *
     * @param <T> the requested instance type
     * @param type the requested type; must not be {@code null}
     * @return the held instance cast to {@code type}, or {@code null} when absent or not assignable
     * @throws NullPointerException if {@code type} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(final Class<T> type) {
        Objects.requireNonNull(type, "type is null");
        var current = instance;
        return type.isInstance(current) ? (T) current : null;
    }

    /**
     * Clears the currently held instance.
     */
    public void clear() {
        this.instance = null;
    }
}
