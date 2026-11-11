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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scope-local storage for the fixtures bound while a {@code Scope} runs.
 *
 * <p>Every {@code Scope} activation owns one frame whose parent is the frame of the enclosing scope, so the
 * frames form a chain that follows the action tree. A frame is passed explicitly through
 * {@link ConcreteContext}, never through a thread-local, so children running on other threads see the same
 * frame.
 *
 * <p>A binding records the fixture's <em>declared</em> type as well as its value, because that is what makes a
 * typed step unambiguous: a lookup walks from the innermost frame outward and, in each frame, prefers an exact
 * declared-type match, then a declared type assignable to the requested type, and only then a runtime
 * {@code instanceof} match. Nested fixtures that share a supertype therefore resolve to the nearest fixture whose
 * declared type fits, not to whichever value happened to be bound first.
 *
 * <p>Instances are thread-safe. They are internal and not part of the public API.
 */
public final class ScopeFrame {

    private final ScopeFrame parent;
    private final CopyOnWriteArrayList<Binding> bindings = new CopyOnWriteArrayList<>();

    private ScopeFrame(final ScopeFrame parent) {
        this.parent = parent;
    }

    /**
     * One fixture binding: the type the fixture was declared as, and its value.
     *
     * @param declaredType the declared fixture type; never {@code null}
     * @param value the fixture; never {@code null}
     */
    private record Binding(Class<?> declaredType, Object value) {}

    /**
     * Creates the outermost frame of a run.
     *
     * @return a new root frame; never {@code null}
     */
    public static ScopeFrame root() {
        return new ScopeFrame(null);
    }

    /**
     * Creates a frame for a nested scope.
     *
     * @return a new frame whose parent is this frame; never {@code null}
     */
    public ScopeFrame child() {
        return new ScopeFrame(this);
    }

    /**
     * Binds a fixture in this frame under its declared type.
     *
     * @param declaredType the type the fixture was declared as; must not be {@code null}
     * @param value the fixture; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public void bind(final Class<?> declaredType, final Object value) {
        bindings.add(new Binding(
                Objects.requireNonNull(declaredType, "declaredType is null"),
                Objects.requireNonNull(value, "value is null")));
    }

    /**
     * Finds the nearest bound value assignable to the requested type, searching this frame and then its
     * ancestors.
     *
     * @param <T> the requested type
     * @param type the requested type; must not be {@code null}
     * @return the value, or {@code null} when none is bound
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <T> T find(final Class<T> type) {
        Objects.requireNonNull(type, "type is null");
        for (var frame = this; frame != null; frame = frame.parent) {
            var local = frame.findLocal(type);
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    /**
     * Finds a fixture in this frame only.
     *
     * <p>An exact declared-type match wins, then a binding whose declared type is assignable to the requested
     * type, then a runtime {@code instanceof} match. Within one category the most recent binding wins.
     *
     * @param <T> the requested type
     * @param type the requested type; must not be {@code null}
     * @return the value, or {@code null} when this frame has none
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <T> T findLocal(final Class<T> type) {
        Objects.requireNonNull(type, "type is null");
        var snapshot = bindings.toArray(new Binding[0]);
        for (var i = snapshot.length - 1; i >= 0; i--) {
            if (snapshot[i].declaredType() == type) {
                return type.cast(snapshot[i].value());
            }
        }
        for (var i = snapshot.length - 1; i >= 0; i--) {
            if (type.isAssignableFrom(snapshot[i].declaredType())) {
                return type.cast(snapshot[i].value());
            }
        }
        for (var i = snapshot.length - 1; i >= 0; i--) {
            if (type.isInstance(snapshot[i].value())) {
                return type.cast(snapshot[i].value());
            }
        }
        return null;
    }

    /**
     * Removes and returns the most recently bound value in this frame only that is assignable to the requested
     * type.
     *
     * @param <T> the requested type
     * @param type the requested type; must not be {@code null}
     * @return the removed value, or {@code null} when this frame has none
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public <T> T removeLocal(final Class<T> type) {
        Objects.requireNonNull(type, "type is null");
        var found = findLocal(type);
        if (found == null) {
            return null;
        }
        for (var binding : bindings) {
            if (binding.value() == found) {
                bindings.remove(binding);
                break;
            }
        }
        return found;
    }
}
